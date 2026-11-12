package co.wethinkcode.healthsafe;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.javalin.Javalin;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

public class WardServiceApp {

    private static final String DEFAULT_INGESTION_URL = "http://localhost:7030";
    private static final ObjectMapper MAPPER = new ObjectMapper();

    public static void main(String[] args) {
        String ingestionUrl = envOr("INGESTION_URL", DEFAULT_INGESTION_URL);
        String brokerUrl = envOr("HEALTHSAFE_BROKER_URL", "tcp://localhost:61616");
        IngestionClient client = new IngestionClient(ingestionUrl);
        WardStore store = new WardStore();
        try {
            store.replaceAll(client.fetchWards());
        } catch (IngestionUnavailableException ex) {
            System.err.println("Could not load wards at startup: " + ex.getMessage());
        }

        StaffingUpdateStore updateStore = new StaffingUpdateStore();
        StaffingEventSubscriber subscriber = new StaffingEventSubscriber(brokerUrl, updateStore);
        try {
            subscriber.start();
        } catch (Exception ex) {
            System.err.println("Could not subscribe to staffing updates: " + ex.getMessage());
        }

        EquipmentPublisher equipmentPublisher = new EquipmentFailurePublisher(brokerUrl);
        createApp(store, client, updateStore, equipmentPublisher).start(7031);
    }

    public static Javalin createApp(WardStore store, IngestionClient client) {
        return createApp(store, client, new StaffingUpdateStore());
    }

    public static Javalin createApp(WardStore store, IngestionClient client,
                                    StaffingUpdateStore updateStore) {
        return createApp(store, client, updateStore, message -> {
        });
    }

    public static Javalin createApp(WardStore store, IngestionClient client,
                                    StaffingUpdateStore updateStore, EquipmentPublisher equipmentPublisher) {
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
        app.get("/staffing-updates", ctx -> ctx.json(updateStore.all()));
        app.get("/staffing-updates/{wardId}", ctx -> updateStore.find(ctx.pathParam("wardId"))
                .ifPresentOrElse(ctx::json,
                        () -> ctx.status(404).json(Map.of("error", "no staffing update for that ward"))));
        app.post("/wards/{id}/equipment-failures", ctx -> {
            Ward ward = store.find(ctx.pathParam("id")).orElse(null);
            if (ward == null) {
                ctx.status(404).json(Map.of("error", "unknown ward"));
                return;
            }
            JsonNode body = parseJson(ctx.body());
            String equipmentId = text(body, "equipmentId");
            if (equipmentId == null || equipmentId.isBlank()) {
                ctx.status(400).json(Map.of("error", "equipmentId is required"));
                return;
            }
            String severity = text(body, "severity");
            EquipmentFailureMessage message = new EquipmentFailureMessage(
                    UUID.randomUUID().toString(),
                    "EQUIPMENT_FAILURE",
                    WardIds.canonical(ward.wardId()),
                    equipmentId.trim(),
                    text(body, "description"),
                    severity == null || severity.isBlank() ? "CRITICAL" : severity.trim(),
                    Instant.now().toString());
            try {
                equipmentPublisher.publish(message);
                ctx.status(202).json(message);
            } catch (EquipmentPublishException ex) {
                ctx.status(503).json(Map.of("error", "equipment alert queue unavailable"));
            }
        });

        return app;
    }

    private static JsonNode parseJson(String body) {
        if (body == null || body.isBlank()) {
            return null;
        }
        try {
            return MAPPER.readTree(body);
        } catch (Exception ex) {
            return null;
        }
    }

    private static String text(JsonNode node, String field) {
        if (node == null) {
            return null;
        }
        JsonNode value = node.get(field);
        return value != null && value.isTextual() ? value.textValue() : null;
    }

    private static String envOr(String name, String fallback) {
        String value = System.getenv(name);
        return value == null || value.isBlank() ? fallback : value;
    }
}
