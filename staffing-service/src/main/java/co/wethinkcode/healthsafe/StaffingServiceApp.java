package co.wethinkcode.healthsafe;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.javalin.Javalin;
import io.javalin.http.Context;

import java.util.Map;

public class StaffingServiceApp {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final String DEFAULT_WARD_URL = "http://localhost:7031";
    private static final String DEFAULT_ALERT_URL = "http://localhost:7032";

    public static void main(String[] args) {
        StaffingService service = new StaffingService(
                new WardClient(envOr("WARD_SERVICE_URL", DEFAULT_WARD_URL)),
                new AlertLevelClient(envOr("ALERT_LEVEL_SERVICE_URL", DEFAULT_ALERT_URL)),
                new ScheduleCalculator(),
                new ScheduleStore());
        createApp(service).start(7033);
    }

    public static Javalin createApp(StaffingService service) {
        Javalin app = Javalin.create();

        app.get("/health", ctx -> ctx.result("OK"));
        app.get("/staffing", ctx -> handle(ctx, () -> {
            String wardId = ctx.queryParam("wardId");
            if (wardId == null || wardId.isBlank()) {
                ctx.status(400).json(Map.of("error", "wardId is required"));
                return;
            }
            ctx.json(service.calculate(wardId));
        }));
        app.post("/schedules", ctx -> handle(ctx, () -> {
            String wardId = parseWardId(ctx.body());
            if (wardId == null || wardId.isBlank()) {
                ctx.status(400).json(Map.of("error", "a non-blank wardId is required"));
                return;
            }
            Schedule schedule = service.store(wardId);
            ctx.status(201)
                    .header("Location", "/schedules/" + schedule.wardId())
                    .json(schedule);
        }));
        app.get("/schedules/{wardId}", ctx -> service.store().find(ctx.pathParam("wardId"))
                .ifPresentOrElse(ctx::json,
                        () -> ctx.status(404).json(Map.of("error", "no schedule for that ward"))));

        return app;
    }

    private static void handle(Context ctx, Runnable action) {
        try {
            action.run();
        } catch (DownstreamNotFoundException ex) {
            ctx.status(404).json(Map.of("error", ex.getMessage()));
        } catch (DownstreamBadGatewayException ex) {
            ctx.status(502).json(Map.of("error", ex.getMessage()));
        } catch (DownstreamUnavailableException ex) {
            ctx.status(503).json(Map.of("error", ex.getMessage()));
        }
    }

    private static String parseWardId(String body) {
        if (body == null || body.isBlank()) {
            return null;
        }
        try {
            JsonNode node = MAPPER.readTree(body).get("wardId");
            return node != null && node.isTextual() ? node.textValue() : null;
        } catch (Exception ex) {
            return null;
        }
    }

    private static String envOr(String name, String fallback) {
        String value = System.getenv(name);
        return value == null || value.isBlank() ? fallback : value;
    }
}
