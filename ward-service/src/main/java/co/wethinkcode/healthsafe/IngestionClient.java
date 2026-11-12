package co.wethinkcode.healthsafe;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;

public final class IngestionClient {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final TypeReference<List<Ward>> WARD_LIST = new TypeReference<>() {
    };

    private final String baseUrl;
    private final HttpClient client;

    public IngestionClient(String baseUrl) {
        this(baseUrl, HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(3)).build());
    }

    IngestionClient(String baseUrl, HttpClient client) {
        this.baseUrl = baseUrl.replaceAll("/+$", "");
        this.client = client;
    }

    public List<Ward> fetchWards() {
        try {
            HttpRequest request = HttpRequest.newBuilder(URI.create(baseUrl + "/wards"))
                    .timeout(Duration.ofSeconds(5))
                    .GET()
                    .build();
            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() != 200) {
                throw new IngestionUnavailableException(
                        "ingestion-service returned HTTP " + response.statusCode());
            }
            List<Ward> wards = MAPPER.readValue(response.body(), WARD_LIST);
            if (wards == null) {
                throw new IngestionUnavailableException("ingestion-service returned no ward list");
            }
            return wards;
        } catch (IngestionUnavailableException ex) {
            throw ex;
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new IngestionUnavailableException("interrupted while calling ingestion-service", ex);
        } catch (Exception ex) {
            throw new IngestionUnavailableException("could not load wards from ingestion-service", ex);
        }
    }
}
