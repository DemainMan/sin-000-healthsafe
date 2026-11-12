package co.wethinkcode.healthsafe;

import io.javalin.Javalin;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.StringReader;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class IngestionServiceAppTest {

    private Javalin app;
    private HttpClient client;

    @BeforeEach
    void start() throws Exception {
        IngestionStore store = IngestionStore.fromReader(new StringReader("""
                ward_id,wing,department,beds_available
                w-01,east wing,cardiology,3
                W-02,west wing,paediatrics,N/A
                """));
        app = IngestionServiceApp.createApp(store).start(0);
        client = HttpClient.newHttpClient();
    }

    @AfterEach
    void stop() {
        app.stop();
    }

    @Test
    void exposesCleanedWardsAndReport() throws Exception {
        HttpResponse<String> wards = get("/wards");
        assertEquals(200, wards.statusCode());
        assertTrue(wards.body().contains("\"wardId\":\"W-01\""));
        assertTrue(wards.body().contains("\"department\":\"Cardiology\""));

        HttpResponse<String> report = get("/ingestion-report");
        assertEquals(200, report.statusCode());
        assertTrue(report.body().contains("\"uniqueWards\":2"));
        assertTrue(report.body().contains("\"flaggedWards\":1"));
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
