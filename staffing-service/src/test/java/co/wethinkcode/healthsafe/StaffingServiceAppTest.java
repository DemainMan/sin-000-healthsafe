package co.wethinkcode.healthsafe;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import io.javalin.Javalin;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.net.InetSocketAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class StaffingServiceAppTest {

    private HttpServer downstream;
    private Javalin app;
    private HttpClient client;
    private final AtomicInteger wardStatus = new AtomicInteger(200);
    private final AtomicInteger alertStatus = new AtomicInteger(200);

    @BeforeEach
    void start() throws Exception {
        downstream = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
        downstream.createContext("/wards", exchange -> {
            if (exchange.getRequestURI().getPath().endsWith("W-99")) {
                send(exchange, 404, "{\"error\":\"unknown ward\"}");
                return;
            }
            if (wardStatus.get() != 200) {
                send(exchange, wardStatus.get(), "{}");
                return;
            }
            send(exchange, 200, """
                    {"wardId":"W-05","wing":"East Wing","department":"Paediatrics",
                     "bedsAvailable":5,"notes":[]}
                    """);
        });
        downstream.createContext("/alert-level", exchange -> {
            if (alertStatus.get() != 200) {
                send(exchange, alertStatus.get(), "{}");
                return;
            }
            send(exchange, 200, "{\"level\":5}");
        });
        downstream.start();

        String base = "http://localhost:" + downstream.getAddress().getPort();
        StaffingService service = new StaffingService(
                new WardClient(base),
                new AlertLevelClient(base),
                new ScheduleCalculator(),
                new ScheduleStore());
        app = StaffingServiceApp.createApp(service).start(0);
        client = HttpClient.newHttpClient();
    }

    @AfterEach
    void stop() {
        app.stop();
        downstream.stop(0);
    }

    @Test
    void calculatesScheduleFromWardAndAlertLevel() throws Exception {
        HttpResponse<String> response = get("/staffing?wardId=W-05");
        assertEquals(200, response.statusCode());
        assertTrue(response.body().contains("\"emergencyLevel\":5"));
        assertTrue(response.body().contains("\"requiredDoctors\":6"));
        assertTrue(response.body().contains("\"onCallWindowHours\":12"));
    }

    @Test
    void storesAndReturnsSchedules() throws Exception {
        HttpResponse<String> created = send("POST", "/schedules", "{\"wardId\":\"W-05\"}");
        assertEquals(201, created.statusCode());
        assertEquals("/schedules/W-05", created.headers().firstValue("Location").orElseThrow());

        HttpResponse<String> stored = get("/schedules/W-05");
        assertEquals(200, stored.statusCode());
        assertTrue(stored.body().contains("\"doctors\":[\"DOC-001\",\"DOC-002\""));
        assertEquals(404, get("/schedules/W-99").statusCode());
    }

    @Test
    void mapsDownstreamErrors() throws Exception {
        assertEquals(404, get("/staffing?wardId=W-99").statusCode());

        wardStatus.set(500);
        assertEquals(502, get("/staffing?wardId=W-05").statusCode());

        wardStatus.set(200);
        alertStatus.set(503);
        assertEquals(502, get("/staffing?wardId=W-05").statusCode());

        downstream.stop(0);
        assertEquals(503, get("/staffing?wardId=W-05").statusCode());
    }

    @Test
    void rejectsMissingOrMalformedWardIds() throws Exception {
        assertEquals(400, get("/staffing").statusCode());
        assertEquals(400, send("POST", "/schedules", "{}").statusCode());
        assertEquals(400, send("POST", "/schedules", "not json").statusCode());
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

    private HttpResponse<String> send(String method, String path, String body) throws Exception {
        return client.send(
                HttpRequest.newBuilder(URI.create("http://localhost:" + app.port() + path))
                        .header("Content-Type", "application/json")
                        .method(method, HttpRequest.BodyPublishers.ofString(body))
                        .build(),
                HttpResponse.BodyHandlers.ofString());
    }

    private static void send(HttpExchange exchange, int status, String body) throws java.io.IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().add("Content-Type", "application/json");
        exchange.sendResponseHeaders(status, bytes.length);
        exchange.getResponseBody().write(bytes);
        exchange.close();
    }
}
