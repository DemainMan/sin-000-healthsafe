package co.wethinkcode.healthsafe;

import java.io.IOException;
import java.io.InputStream;
import java.io.Reader;
import java.util.List;

public final class IngestionStore {

    private final List<Ward> wards;
    private final IngestionReport report;

    public IngestionStore(List<Ward> wards, IngestionReport report) {
        this.wards = List.copyOf(wards);
        this.report = report;
    }

    public static IngestionStore fromClasspath(String resource) throws IOException {
        try (InputStream input = IngestionStore.class.getResourceAsStream(resource)) {
            if (input == null) {
                throw new IOException("Classpath resource not found: " + resource);
            }
            return fromReader(new java.io.InputStreamReader(input, java.nio.charset.StandardCharsets.UTF_8));
        }
    }

    public static IngestionStore fromReader(Reader reader) throws IOException {
        IngestionResult result = new WardCsvCleaner().clean(reader);
        return new IngestionStore(result.wards(), result.report());
    }

    public List<Ward> wards() {
        return wards;
    }

    public IngestionReport report() {
        return report;
    }
}
