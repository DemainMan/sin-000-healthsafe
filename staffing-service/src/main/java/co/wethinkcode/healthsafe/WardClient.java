package co.wethinkcode.healthsafe;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;

public final class WardClient {

    private static final ObjectMapper MAPPER = new ObjectMapper()
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

    private final String baseUrl;
    private final HttpClient client;

    public WardClient(String baseUrl) {
        this(baseUrl, HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(3)).build());
    }

    WardClient(String baseUrl, HttpClient client) {
        this.baseUrl = baseUrl.replaceAll("/+$", "");
        this.client = client;
    }

    public Ward fetch(String wardId) {
        String encoded = URLEncoder.encode(wardId, StandardCharsets.UTF_8);
        try {
            HttpRequest request = HttpRequest.newBuilder(URI.create(baseUrl + "/wards/" + encoded))
                    .timeout(Duration.ofSeconds(5))
                    .GET()
                    .build();
            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() == 404) {
                throw new DownstreamNotFoundException("unknown ward " + wardId);
            }
            if (response.statusCode() != 200) {
                throw new DownstreamBadGatewayException(
                        "ward-service returned HTTP " + response.statusCode());
            }
            Ward ward = MAPPER.readValue(response.body(), Ward.class);
            if (ward == null || WardIds.canonical(ward.wardId()) == null) {
                throw new DownstreamBadGatewayException("ward-service returned an invalid ward");
            }
            return ward;
        } catch (DownstreamNotFoundException | DownstreamBadGatewayException ex) {
            throw ex;
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new DownstreamUnavailableException("interrupted while calling ward-service", ex);
        } catch (Exception ex) {
            throw new DownstreamUnavailableException("could not call ward-service", ex);
        }
    }
}
