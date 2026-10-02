package com.mapmory.backend.place.infrastructure.geoapify;

import com.mapmory.backend.common.exception.BusinessException;
import com.mapmory.backend.place.application.PlaceErrorCode;
import com.mapmory.backend.place.application.model.PlaceCandidate;
import com.mapmory.backend.place.application.model.PlaceDetails;
import com.mapmory.backend.place.application.port.PlaceLookupPort;
import java.net.URI;
import java.util.List;
import java.util.function.Function;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatusCode;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.util.UriBuilder;
import tools.jackson.databind.JsonNode;

@Component
public class GeoapifyClient implements PlaceLookupPort {

    private static final String PROVIDER_CODE = "GEOAPIFY";

    private final RestClient restClient;
    private final String apiKey;
    private final GeoapifyRequestLimiter requestLimiter;
    private final GeoapifyPlaceMapper mapper = new GeoapifyPlaceMapper();

    public GeoapifyClient(
            RestClient.Builder builder,
            @Value("${geoapify.base-url:https://api.geoapify.com}") String baseUrl,
            @Value("${geoapify.api-key:}") String apiKey,
            GeoapifyRequestLimiter requestLimiter
    ) {
        this.restClient = builder.baseUrl(baseUrl).build();
        this.apiKey = apiKey;
        this.requestLimiter = requestLimiter;
    }

    @Override
    public String providerCode() {
        return PROVIDER_CODE;
    }

    @Override
    public List<PlaceCandidate> search(String query) {
        requireConfigured();
        requestLimiter.reserveSearch();
        JsonNode response = get(uri -> uri.path("/v1/geocode/autocomplete")
                .queryParam("text", query)
                .queryParam("format", "json")
                .queryParam("lang", "ko")
                .queryParam("limit", 10)
                .queryParam("apiKey", apiKey)
                .build());

        return mapper.candidates(response);
    }

    @Override
    public PlaceDetails findById(String placeId) {
        if (placeId == null || !placeId.matches("[A-Za-z0-9_-]{1,255}")) {
            throw new BusinessException(PlaceErrorCode.INVALID_PLACE_ID);
        }
        requireConfigured();
        requestLimiter.reserveDetails();
        JsonNode response = get(uri -> uri.path("/v2/place-details")
                .queryParam("id", placeId)
                .queryParam("lang", "ko")
                .queryParam("apiKey", apiKey)
                .build());

        return mapper.details(response, placeId)
                .orElseThrow(() -> new BusinessException(PlaceErrorCode.PLACE_NOT_FOUND));
    }

    private JsonNode get(Function<UriBuilder, URI> uri) {
        try {
            JsonNode body = restClient.get().uri(uri).retrieve().body(JsonNode.class);
            if (body == null || body.isNull()) {
                throw new BusinessException(PlaceErrorCode.PLACE_PROVIDER_UNAVAILABLE);
            }
            return body;
        } catch (RestClientResponseException exception) {
            HttpStatusCode status = exception.getStatusCode();
            if (status.value() == 400 || status.value() == 404) {
                throw new BusinessException(PlaceErrorCode.PLACE_NOT_FOUND);
            }
            throw unavailable();
        } catch (RestClientException exception) {
            throw unavailable();
        }
    }

    private void requireConfigured() {
        if (apiKey == null || apiKey.isBlank()) {
            throw new BusinessException(PlaceErrorCode.PLACE_PROVIDER_UNAVAILABLE,
                    "GEOAPIFY_API_KEY가 설정되지 않았습니다.");
        }
    }

    private static BusinessException unavailable() {
        // RestClient 예외 메시지에는 query parameter의 API 키가 포함될 수 있다.
        return new BusinessException(PlaceErrorCode.PLACE_PROVIDER_UNAVAILABLE);
    }
}
