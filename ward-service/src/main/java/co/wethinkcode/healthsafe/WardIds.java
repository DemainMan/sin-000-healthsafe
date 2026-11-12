package co.wethinkcode.healthsafe;

import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class WardIds {

    private static final Pattern WARD_ID = Pattern.compile("(?i)^(?:w(?:ard)?[-_]?)?(\\d{1,3})$");

    private WardIds() {
    }

    public static String canonical(String raw) {
        if (raw == null) {
            return null;
        }
        Matcher matcher = WARD_ID.matcher(raw.replaceAll("\\s+", ""));
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
}
