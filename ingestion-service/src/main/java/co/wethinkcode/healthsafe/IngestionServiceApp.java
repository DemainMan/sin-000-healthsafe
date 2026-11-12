package co.wethinkcode.healthsafe;

import io.javalin.Javalin;

public class IngestionServiceApp {

    private static final String CSV_RESOURCE = "/wards-outdated.csv";

    public static void main(String[] args) throws Exception {
        IngestionStore store = IngestionStore.fromClasspath(CSV_RESOURCE);
        createApp(store).start(7030);
    }

    public static Javalin createApp(IngestionStore store) {
        Javalin app = Javalin.create();

        app.get("/health", ctx -> ctx.result("OK"));
        app.get("/wards", ctx -> ctx.json(store.wards()));
        app.get("/ingestion-report", ctx -> ctx.json(store.report()));

        return app;
    }
}
