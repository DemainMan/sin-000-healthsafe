package co.wethinkcode.healthsafe;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

public final class AlertLevelClient {

    private static final ObjectMapper MAPPER = new ObjectMapper()
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

    private final String baseUrl;
    private final HttpClient client;

    public AlertLevelClient(String baseUrl) {
        this(baseUrl, HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(3)).build());
    }

    AlertLevelClient(String baseUrl, HttpClient client) {
        this.baseUrl = baseUrl.replaceAll("/+$", "");
        this.client = client;
    }

    public AlertLevel fetchLevel() {
        try {
            HttpRequest request = HttpRequest.newBuilder(URI.create(baseUrl + "/alert-level"))
                    .timeout(Duration.ofSeconds(5))
                    .GET()
                    .build();
            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() != 200) {
                throw new DownstreamBadGatewayException(
                        "alert-level-service returned HTTP " + response.statusCode());
            }
            AlertLevel level = MAPPER.readValue(response.body(), AlertLevel.class);
            if (level == null || level.level() < 0 || level.level() > 8) {
                throw new DownstreamBadGatewayException("alert-level-service returned an invalid level");
            }
            return level;
        } catch (DownstreamBadGatewayException ex) {
            throw ex;
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new DownstreamUnavailableException("interrupted while calling alert-level-service", ex);
        } catch (Exception ex) {
            throw new DownstreamUnavailableException("could not call alert-level-service", ex);
        }
    }
}
