package co.wethinkcode.healthsafe;

import com.fasterxml.jackson.databind.ObjectMapper;
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
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WardServiceAppTest {

    private HttpServer ingestion;
    private Javalin app;
    private HttpClient client;
    private StaffingUpdateStore updateStore;

    @BeforeEach
    void start() throws Exception {
        ingestion = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
        ingestion.createContext("/wards", exchange -> {
            byte[] body = """
                    [
                      {"wardId":"W-01","wing":"East Wing","department":"Cardiology","bedsAvailable":3},
                      {"wardId":"w-02","wing":"West Wing","department":"Paediatrics","bedsAvailable":null}
                    ]
                    """.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, body.length);
            exchange.getResponseBody().write(body);
            exchange.close();
        });
        ingestion.start();

        IngestionClient ingestionClient = new IngestionClient(
                "http://localhost:" + ingestion.getAddress().getPort());
        WardStore store = new WardStore();
        store.replaceAll(ingestionClient.fetchWards());
        updateStore = new StaffingUpdateStore();
        app = WardServiceApp.createApp(store, ingestionClient, updateStore).start(0);
        client = HttpClient.newHttpClient();
    }

    @AfterEach
    void stop() {
        app.stop();
        ingestion.stop(0);
    }

    @Test
    void listsWardsAndDepartments() throws Exception {
        HttpResponse<String> wards = get("/wards");
        assertEquals(200, wards.statusCode());
        assertTrue(wards.body().contains("\"wardId\":\"W-01\""));

        HttpResponse<String> departments = get("/departments");
        assertEquals(200, departments.statusCode());
        assertEquals(new ObjectMapper().readValue(departments.body(), List.class),
                List.of("Cardiology", "Paediatrics"));
    }

    @Test
    void resolvesCanonicalWardIdsAndReturns404ForUnknownWards() throws Exception {
        HttpResponse<String> found = get("/wards/w-1");
        assertEquals(200, found.statusCode());
        assertTrue(found.body().contains("\"wardId\":\"W-01\""));

        assertEquals(404, get("/wards/W-99").statusCode());
        assertEquals(404, get("/wards/not-a-ward").statusCode());
    }

    @Test
    void refreshesFromIngestion() throws Exception {
        HttpResponse<String> refreshed = sendPost("/wards/refresh");
        assertEquals(200, refreshed.statusCode());
        assertTrue(refreshed.body().contains("\"wards\":2"));
    }

    @Test
    void exposesLatestStaffingUpdates() throws Exception {
        updateStore.save(new StaffingEvent(
                "SCHEDULE_UPDATED", "w-01", "East Wing", "Cardiology", 4, 5,
                List.of("DOC-001"), 12, "2026-01-01T00:00:00Z"));

        HttpResponse<String> all = get("/staffing-updates");
        assertEquals(200, all.statusCode());
        assertTrue(all.body().contains("\"wardId\":\"w-01\""));

        HttpResponse<String> one = get("/staffing-updates/W-01");
        assertEquals(200, one.statusCode());
        assertTrue(one.body().contains("\"requiredDoctors\":5"));
        assertEquals(404, get("/staffing-updates/W-99").statusCode());
    }

    @Test
    void returns503WhenIngestionFailsDuringRefresh() throws Exception {
        ingestion.stop(0);
        HttpResponse<String> response = sendPost("/wards/refresh");
        assertEquals(503, response.statusCode());
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

    private HttpResponse<String> sendPost(String path) throws Exception {
        return client.send(
                HttpRequest.newBuilder(URI.create("http://localhost:" + app.port() + path))
                        .POST(HttpRequest.BodyPublishers.noBody())
                        .build(),
                HttpResponse.BodyHandlers.ofString());
    }
}
