package co.wethinkcode.healthsafe;

import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.Test;

import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class IngestionClientTest {

    @Test
    void loadsWardsFromIngestion() throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
        server.createContext("/wards", exchange -> {
            byte[] body = """
                    [{"wardId":"W-01","wing":"East Wing","department":"Cardiology","bedsAvailable":3}]
                    """.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, body.length);
            exchange.getResponseBody().write(body);
            exchange.close();
        });
        server.start();
        try {
            List<Ward> wards = new IngestionClient("http://localhost:" + server.getAddress().getPort())
                    .fetchWards();
            assertEquals(1, wards.size());
            assertEquals("W-01", wards.get(0).wardId());
        } finally {
            server.stop(0);
        }
    }

    @Test
    void reportsNonSuccessResponses() throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
        server.createContext("/wards", exchange -> {
            exchange.sendResponseHeaders(500, -1);
            exchange.close();
        });
        server.start();
        try {
            IngestionClient client = new IngestionClient("http://localhost:" + server.getAddress().getPort());
            assertThrows(IngestionUnavailableException.class, client::fetchWards);
        } finally {
            server.stop(0);
        }
    }
}
