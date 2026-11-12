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

class AlertLevelServiceAppTest {

    private Javalin app;
    private HttpClient client;

    @BeforeEach
    void start() {
        app = AlertLevelServiceApp.createApp(new AlertLevelStore()).start(0);
        client = HttpClient.newHttpClient();
    }

    @AfterEach
    void stop() {
        app.stop();
    }

    @Test
    void defaultsToZeroAndUpdatesWithPutAndPost() throws Exception {
        assertEquals(200, get("/alert-level").statusCode());
        assertTrue(get("/alert-level").body().contains("\"level\":0"));

        HttpResponse<String> put = send("PUT", "{\"level\":5}");
        assertEquals(200, put.statusCode());
        assertTrue(put.body().contains("\"level\":5"));
        assertTrue(get("/alert-level").body().contains("\"level\":5"));

        HttpResponse<String> post = send("POST", "{\"level\":8}");
        assertEquals(200, post.statusCode());
        assertTrue(post.body().contains("\"level\":8"));
    }

    @Test
    void rejectsInvalidLevelsWithoutChangingState() throws Exception {
        send("PUT", "{\"level\":4}");

        for (String body : new String[]{
                "{\"level\":-1}",
                "{\"level\":9}",
                "{\"level\":2.5}",
                "{\"level\":\"5\"}",
                "{}",
                "not json"
        }) {
            HttpResponse<String> response = send("PUT", body);
            assertEquals(400, response.statusCode(), body);
        }
        assertTrue(get("/alert-level").body().contains("\"level\":4"));
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

    private HttpResponse<String> send(String method, String body) throws Exception {
        return client.send(
                HttpRequest.newBuilder(URI.create("http://localhost:" + app.port() + "/alert-level"))
                        .header("Content-Type", "application/json")
                        .method(method, HttpRequest.BodyPublishers.ofString(body))
                        .build(),
                HttpResponse.BodyHandlers.ofString());
    }
}
