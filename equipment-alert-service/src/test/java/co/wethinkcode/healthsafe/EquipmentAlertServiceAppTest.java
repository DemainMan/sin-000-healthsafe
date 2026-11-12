package co.wethinkcode.healthsafe;

import io.javalin.Javalin;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EquipmentAlertServiceAppTest {

    private EquipmentAlertStore store;
    private Javalin app;
    private HttpClient client;

    @BeforeEach
    void start() {
        store = new EquipmentAlertStore();
        app = EquipmentAlertServiceApp.createApp(store).start(0);
        client = HttpClient.newHttpClient();
    }

    @AfterEach
    void stop() {
        app.stop();
    }

    @Test
    void listsAndReturnsStoredAlerts() throws Exception {
        store.save(new EquipmentAlert("alert-1", "EQUIPMENT_FAILURE", "W-01",
                "EQ-1", "Ventilator stopped", "CRITICAL", "2026-01-01T00:00:00Z"));

        HttpResponse<String> all = get("/alerts");
        assertEquals(200, all.statusCode());
        assertTrue(all.body().contains("\"alertId\":\"alert-1\""));

        HttpResponse<String> one = get("/alerts/alert-1");
        assertEquals(200, one.statusCode());
        assertTrue(one.body().contains("\"wardId\":\"W-01\""));
        assertEquals(404, get("/alerts/unknown").statusCode());
    }

    @Test
    void exposesHealth() throws Exception {
        assertEquals("OK", get("/health").body());
    }

    private HttpResponse<String> get(String path) throws Exception {
        return client.send(
                HttpRequest.newBuilder(URI.create("http://localhost:" + app.port() + path)).GET().build(),
                HttpResponse.BodyHandlers.ofString());
    }
}
