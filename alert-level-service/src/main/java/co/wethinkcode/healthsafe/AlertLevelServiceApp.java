package co.wethinkcode.healthsafe;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.javalin.Javalin;
import io.javalin.http.Context;

import java.util.Map;

public class AlertLevelServiceApp {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    public static void main(String[] args) {
        createApp(new AlertLevelStore()).start(7032);
    }

    public static Javalin createApp(AlertLevelStore store) {
        Javalin app = Javalin.create();

        app.get("/health", ctx -> ctx.result("OK"));
        app.get("/alert-level", ctx -> ctx.json(new AlertLevel(store.current())));
        app.put("/alert-level", ctx -> update(ctx, store));
        app.post("/alert-level", ctx -> update(ctx, store));

        return app;
    }

    private static void update(Context ctx, AlertLevelStore store) {
        Integer requested = parseLevel(ctx.body());
        if (requested == null || !AlertLevelStore.isValid(requested)) {
            ctx.status(400).json(Map.of("error", "level must be an integer between 0 and 8"));
            return;
        }
        store.set(requested);
        ctx.json(new AlertLevel(store.current()));
    }

    private static Integer parseLevel(String body) {
        if (body == null || body.isBlank()) {
            return null;
        }
        try {
            JsonNode node = MAPPER.readTree(body).get("level");
            return node != null && node.isIntegralNumber() && node.canConvertToInt()
                    ? node.intValue()
                    : null;
        } catch (Exception ex) {
            return null;
        }
    }
}
