package co.wethinkcode.healthsafe;

import io.javalin.Javalin;

import java.util.Map;

public class WardServiceApp {

    private static final String DEFAULT_INGESTION_URL = "http://localhost:7030";

    public static void main(String[] args) {
        String ingestionUrl = envOr("INGESTION_URL", DEFAULT_INGESTION_URL);
        IngestionClient client = new IngestionClient(ingestionUrl);
        WardStore store = new WardStore();
        try {
            store.replaceAll(client.fetchWards());
        } catch (IngestionUnavailableException ex) {
            System.err.println("Could not load wards at startup: " + ex.getMessage());
        }
        createApp(store, client).start(7031);
    }

    public static Javalin createApp(WardStore store, IngestionClient client) {
        Javalin app = Javalin.create();

        app.get("/health", ctx -> ctx.result("OK"));
        app.get("/wards", ctx -> ctx.json(store.all()));
        app.get("/wards/{id}", ctx -> store.find(ctx.pathParam("id"))
                .ifPresentOrElse(ctx::json,
                        () -> ctx.status(404).json(Map.of("error", "unknown ward"))));
        app.get("/departments", ctx -> ctx.json(store.departments()));
        app.post("/wards/refresh", ctx -> {
            try {
                store.replaceAll(client.fetchWards());
                ctx.json(Map.of("wards", store.all().size()));
            } catch (IngestionUnavailableException ex) {
                ctx.status(503).json(Map.of("error", "ingestion-service unavailable"));
            }
        });

        return app;
    }

    private static String envOr(String name, String fallback) {
        String value = System.getenv(name);
        return value == null || value.isBlank() ? fallback : value;
    }
}
