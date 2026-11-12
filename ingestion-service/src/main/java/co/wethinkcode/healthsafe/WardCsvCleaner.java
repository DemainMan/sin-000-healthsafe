package co.wethinkcode.healthsafe;

import com.opencsv.CSVReader;
import com.opencsv.CSVReaderBuilder;
import com.opencsv.exceptions.CsvException;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.time.format.ResolverStyle;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Cleans the deliberately messy legacy ward export.
 *
 * <p>Decisions are intentionally conservative: ambiguous numeric values are
 * nulled and flagged rather than guessed, and duplicate rows are merged with the
 * first non-null value winning. The 0..200 bed range is a demo safeguard, not a
 * real clinical capacity rule.</p>
 */
public final class WardCsvCleaner {

    private static final int MAX_BEDS = 200;

    private static final Set<String> PLACEHOLDERS = Set.of(
            "", "-", "--", "?", "n/a", "na", "nan", "null", "none", "nil",
            "tbd", "tba", "unknown", "unk", "not available", "not applicable"
    );

    private static final Pattern WARD_ID = Pattern.compile("(?i)^(?:w(?:ard)?[-_]?)?(\\d{1,3})$");
    private static final Pattern ISO_DATE = Pattern.compile("^\\d{4}-\\d{1,2}-\\d{1,2}$");
    private static final Pattern SLASH_DATE = Pattern.compile("^\\d{1,2}/\\d{1,2}/\\d{4}$");
    private static final Pattern DASH_DATE = Pattern.compile("^\\d{1,2}-\\d{1,2}-\\d{4}$");

    public IngestionResult clean(InputStream input) throws IOException {
        return clean(new InputStreamReader(input, StandardCharsets.UTF_8));
    }

    public IngestionResult clean(Reader source) throws IOException {
        List<String[]> rows;
        try (CSVReader reader = new CSVReaderBuilder(source).build()) {
            rows = reader.readAll();
        } catch (CsvException ex) {
            throw new IOException("Could not parse ward CSV", ex);
        }
        if (rows.isEmpty()) {
            return new IngestionResult(List.of(), new IngestionReport(0, 0, 0, 0, 0, 0, List.of()));
        }

        Map<String, Integer> columns = columnIndexes(rows.get(0));
        Map<String, ParsedWard> merged = new LinkedHashMap<>();
        List<RejectedRow> rejected = new ArrayList<>();

        int totalRows = 0;
        int acceptedRows = 0;
        int mergedDuplicates = 0;

        for (int i = 1; i < rows.size(); i++) {
            String[] row = rows.get(i);
            if (isBlankRow(row)) {
                continue;
            }
            totalRows++;
            int rowNumber = i + 1;
            String rawId = valueAt(row, columns.getOrDefault("ward_id", -1));
            String wardId = canonicalWardId(rawId);
            if (wardId == null) {
                rejected.add(new RejectedRow(rowNumber, safeRaw(rawId), "missing or unusable ward id"));
                continue;
            }

            acceptedRows++;
            ParsedWard incoming = parseRow(rowNumber, wardId, row, columns);
            ParsedWard existing = merged.get(wardId);
            if (existing == null) {
                merged.put(wardId, incoming);
            } else {
                merge(existing, incoming, rowNumber);
                mergedDuplicates++;
            }
        }

        List<Ward> wards = merged.values().stream()
                .map(ParsedWard::toWard)
                .toList();
        long flaggedWards = wards.stream().filter(w -> !w.notes().isEmpty()).count();
        IngestionReport report = new IngestionReport(
                totalRows,
                acceptedRows,
                wards.size(),
                mergedDuplicates,
                rejected.size(),
                (int) flaggedWards,
                List.copyOf(rejected)
        );
        return new IngestionResult(wards, report);
    }

    private ParsedWard parseRow(int rowNumber, String wardId, String[] row, Map<String, Integer> columns) {
        ParsedWard ward = new ParsedWard(wardId, rowNumber);

        int wingIndex = columns.getOrDefault("wing", -1);
        if (wingIndex >= 0) {
            ward.wing = titleCase(cleanText(valueAt(row, wingIndex)));
            if (ward.wing == null) {
                ward.notes.add("wing was missing or a placeholder");
            }
        }

        int departmentIndex = columns.getOrDefault("department", -1);
        if (departmentIndex >= 0) {
            ward.department = canonicalDepartment(valueAt(row, departmentIndex));
            if (ward.department == null) {
                ward.notes.add("department was missing or a placeholder");
            }
        }

        int bedsIndex = columns.getOrDefault("beds_available", -1);
        if (bedsIndex >= 0) {
            ward.bedsAvailable = parseBeds(valueAt(row, bedsIndex), ward.notes);
        }

        int inspectionIndex = columns.getOrDefault("last_inspection_date", -1);
        if (inspectionIndex >= 0) {
            ward.lastInspectionDate = parseDate(valueAt(row, inspectionIndex), ward.notes);
        }

        int operationalIndex = columns.getOrDefault("operational", -1);
        if (operationalIndex >= 0) {
            ward.operational = parseBoolean(valueAt(row, operationalIndex), ward.notes);
        }

        return ward;
    }

    private void merge(ParsedWard existing, ParsedWard incoming, int duplicateRowNumber) {
        existing.notes.addAll(incoming.notes);
        existing.notes.add("row " + duplicateRowNumber + " was a duplicate of " + existing.wardId);

        existing.wing = mergeField(existing, "wing", existing.wing, incoming.wing, duplicateRowNumber);
        existing.department = mergeField(existing, "department", existing.department,
                incoming.department, duplicateRowNumber);
        existing.bedsAvailable = mergeField(existing, "bedsAvailable", existing.bedsAvailable,
                incoming.bedsAvailable, duplicateRowNumber);
        existing.lastInspectionDate = mergeField(existing, "lastInspectionDate",
                existing.lastInspectionDate, incoming.lastInspectionDate, duplicateRowNumber);
        existing.operational = mergeField(existing, "operational", existing.operational,
                incoming.operational, duplicateRowNumber);
    }

    private <T> T mergeField(ParsedWard existing, String field, T current, T incoming, int rowNumber) {
        if (current == null) {
            return incoming;
        }
        if (incoming != null && !current.equals(incoming)) {
            existing.notes.add("row " + rowNumber + " conflicted on " + field
                    + " (kept '" + current + "', ignored '" + incoming + "')");
        }
        return current;
    }

    private Integer parseBeds(String raw, List<String> notes) {
        String text = cleanText(raw);
        if (text == null) {
            notes.add(raw == null || raw.isBlank()
                    ? "bedsAvailable was missing"
                    : "bedsAvailable was a missing/placeholder value ('" + raw.trim() + "')");
            return null;
        }
        try {
            int beds = Integer.parseInt(text);
            if (beds < 0) {
                notes.add("bedsAvailable was negative ('" + text + "') — flagged for follow-up");
                return null;
            }
            if (beds > MAX_BEDS) {
                notes.add("bedsAvailable was outside 0.." + MAX_BEDS + " ('" + text
                        + "') — flagged as suspect");
                return null;
            }
            return beds;
        } catch (NumberFormatException ex) {
            notes.add("bedsAvailable was non-numeric ('" + text + "') — flagged for follow-up");
            return null;
        }
    }

    private String parseDate(String raw, List<String> notes) {
        String text = cleanText(raw);
        if (text == null) {
            notes.add(raw == null || raw.isBlank()
                    ? "lastInspectionDate was missing"
                    : "lastInspectionDate was a missing/placeholder value ('" + raw.trim() + "')");
            return null;
        }

        String pattern;
        if (ISO_DATE.matcher(text).matches()) {
            pattern = "uuuu-M-d";
        } else if (SLASH_DATE.matcher(text).matches()) {
            pattern = "M/d/uuuu";
        } else if (DASH_DATE.matcher(text).matches()) {
            pattern = "d-M-uuuu";
        } else {
            notes.add("lastInspectionDate was invalid ('" + text + "') — flagged for follow-up");
            return null;
        }

        try {
            DateTimeFormatter formatter = DateTimeFormatter.ofPattern(pattern)
                    .withResolverStyle(ResolverStyle.STRICT);
            return LocalDate.parse(text, formatter).toString();
        } catch (DateTimeParseException ex) {
            notes.add("lastInspectionDate was invalid ('" + text + "') — flagged for follow-up");
            return null;
        }
    }

    private Boolean parseBoolean(String raw, List<String> notes) {
        String text = cleanText(raw);
        if (text == null) {
            notes.add(raw == null || raw.isBlank()
                    ? "operational was missing"
                    : "operational was a missing/placeholder value ('" + raw.trim() + "')");
            return null;
        }
        return switch (text.toLowerCase(Locale.ROOT)) {
            case "y", "yes", "true", "1" -> Boolean.TRUE;
            case "n", "no", "false", "0" -> Boolean.FALSE;
            default -> {
                notes.add("operational was invalid ('" + text + "') — flagged for follow-up");
                yield null;
            }
        };
    }

    private static Map<String, Integer> columnIndexes(String[] header) {
        Map<String, Integer> indexes = new LinkedHashMap<>();
        for (int i = 0; i < header.length; i++) {
            String key = canonicalHeader(header[i]);
            switch (key) {
                case "ward_id", "wardid", "id" -> indexes.putIfAbsent("ward_id", i);
                case "wing" -> indexes.putIfAbsent("wing", i);
                case "department", "dept", "specialist_department", "specialty" ->
                        indexes.putIfAbsent("department", i);
                case "beds_available", "bedsavailable", "beds", "available_beds", "bed_count" ->
                        indexes.putIfAbsent("beds_available", i);
                case "last_inspection_date", "lastinspectiondate", "inspection_date", "last_inspected" ->
                        indexes.putIfAbsent("last_inspection_date", i);
                case "operational", "is_operational", "operational_status", "status" ->
                        indexes.putIfAbsent("operational", i);
                default -> {
                    // Unknown columns are ignored so optional/new export columns do not break ingestion.
                }
            }
        }
        return indexes;
    }

    private static String canonicalHeader(String header) {
        if (header == null) {
            return "";
        }
        return header.trim().toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9]+", "_")
                .replaceAll("^_+|_+$", "");
    }

    public static String canonicalWardId(String raw) {
        if (raw == null) {
            return null;
        }
        String compact = raw.replaceAll("\\s+", "");
        Matcher matcher = WARD_ID.matcher(compact);
        if (!matcher.matches()) {
            return null;
        }
        try {
            int number = Integer.parseInt(matcher.group(1));
            return String.format(Locale.ROOT, "W-%02d", number);
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    private static String canonicalDepartment(String raw) {
        String text = cleanText(raw);
        if (text == null) {
            return null;
        }
        String key = text.toLowerCase(Locale.ROOT).replaceAll("[^a-z]", "");
        return switch (key) {
            case "pediatrics", "paediatrics", "pediatric", "paediatric" -> "Paediatrics";
            case "cardiology" -> "Cardiology";
            case "oncology" -> "Oncology";
            case "radiology" -> "Radiology";
            case "maternity" -> "Maternity";
            case "icu", "intensivecare", "intensivecareunit" -> "ICU";
            default -> titleCase(text);
        };
    }

    private static String titleCase(String text) {
        if (text == null) {
            return null;
        }
        String[] words = text.toLowerCase(Locale.ROOT).split("\\s+");
        StringBuilder result = new StringBuilder();
        for (String word : words) {
            if (word.isEmpty()) {
                continue;
            }
            if (!result.isEmpty()) {
                result.append(' ');
            }
            result.append(Character.toUpperCase(word.charAt(0))).append(word.substring(1));
        }
        return result.toString();
    }

    private static String cleanText(String raw) {
        if (raw == null) {
            return null;
        }
        String text = raw.replace('\u00A0', ' ').trim().replaceAll("\\s+", " ");
        if (PLACEHOLDERS.contains(text.toLowerCase(Locale.ROOT))) {
            return null;
        }
        return text;
    }

    private static String valueAt(String[] row, int index) {
        if (index < 0 || index >= row.length) {
            return null;
        }
        return row[index];
    }

    private static boolean isBlankRow(String[] row) {
        for (String value : row) {
            if (value != null && !value.isBlank()) {
                return false;
            }
        }
        return true;
    }

    private static String safeRaw(String raw) {
        return raw == null ? "" : raw.trim();
    }

    private static final class ParsedWard {
        private final String wardId;
        private final List<String> notes = new ArrayList<>();
        private String wing;
        private String department;
        private Integer bedsAvailable;
        private String lastInspectionDate;
        private Boolean operational;

        private ParsedWard(String wardId, int rowNumber) {
            this.wardId = wardId;
            this.notes.add("sourced from CSV row " + rowNumber);
        }

        private Ward toWard() {
            List<String> cleanedNotes = notes.stream()
                    .filter(note -> !note.startsWith("sourced from CSV row "))
                    .toList();
            return new Ward(wardId, wing, department, bedsAvailable,
                    lastInspectionDate, operational, cleanedNotes);
        }
    }
}
