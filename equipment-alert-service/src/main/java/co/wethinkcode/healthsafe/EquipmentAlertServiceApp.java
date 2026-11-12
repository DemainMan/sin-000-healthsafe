package co.wethinkcode.healthsafe;

import io.javalin.Javalin;

import java.util.Map;

public class EquipmentAlertServiceApp {

    public static void main(String[] args) {
        String brokerUrl = envOr("HEALTHSAFE_BROKER_URL", "tcp://localhost:61616");
        EquipmentAlertStore store = new EquipmentAlertStore();
        EquipmentAlertConsumer consumer = new EquipmentAlertConsumer(brokerUrl, store);
        try {
            consumer.start();
        } catch (Exception ex) {
            System.err.println("Could not start equipment alert consumer: " + ex.getMessage());
        }
        createApp(store).start(7034);
    }

    public static Javalin createApp(EquipmentAlertStore store) {
        Javalin app = Javalin.create();

        app.get("/health", ctx -> ctx.result("OK"));
        app.get("/alerts", ctx -> ctx.json(store.all()));
        app.get("/alerts/{alertId}", ctx -> store.find(ctx.pathParam("alertId"))
                .ifPresentOrElse(ctx::json,
                        () -> ctx.status(404).json(Map.of("error", "unknown equipment alert"))));

        return app;
    }

    private static String envOr(String name, String fallback) {
        String value = System.getenv(name);
        return value == null || value.isBlank() ? fallback : value;
    }
}
