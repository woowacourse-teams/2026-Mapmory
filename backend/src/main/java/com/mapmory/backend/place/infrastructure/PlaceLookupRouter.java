package com.mapmory.backend.place.infrastructure;

import com.mapmory.backend.place.application.model.PlaceCandidate;
import com.mapmory.backend.place.application.model.PlaceDetails;
import com.mapmory.backend.place.application.port.PlaceLookupPort;
import com.mapmory.backend.place.infrastructure.geoapify.GeoapifyClient;
import com.mapmory.backend.place.infrastructure.google.GooglePlacesClient;
import java.util.List;
import java.util.regex.Pattern;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

/**
 * Google 키가 있고 클라이언트가 sessionToken을 보내면 Google 자동완성으로 검색하고, 아니면 Geoapify를 쓴다.
 *
 * <p>sessionToken은 Google 출처 표기와 후보 이름 표시를 지원하는 앱 버전만 보낸다. 그 전 버전은 Geoapify 출처를
 * 고정으로 그리므로 Google 결과를 받으면 표기 정책을 지킬 수 없다. 조회와 저장은 장소 ID 형식으로 제공자를 고른다.
 */
@Primary
@Component
public class PlaceLookupRouter implements PlaceLookupPort {

    static final String GOOGLE_PROVIDER_CODE = "GOOGLE";

    // Geoapify 장소 ID는 소문자 16진수다. Google 장소 ID("ChIJ…")는 대문자를 포함한다.
    private static final Pattern GEOAPIFY_PLACE_ID = Pattern.compile("[0-9a-f]+");

    private final GooglePlacesClient google;
    private final GeoapifyClient geoapify;

    public PlaceLookupRouter(GooglePlacesClient google, GeoapifyClient geoapify) {
        this.google = google;
        this.geoapify = geoapify;
    }

    @Override
    public String providerCode(String placeId) {
        return isGooglePlaceId(placeId) ? GOOGLE_PROVIDER_CODE : geoapify.providerCode(placeId);
    }

    @Override
    public List<PlaceCandidate> search(String query, String sessionToken) {
        if (sessionToken != null && google.isConfigured()) {
            return google.search(query, sessionToken);
        }
        return geoapify.search(query);
    }

    @Override
    public PlaceDetails findForSelection(String placeId, String sessionToken) {
        return isGooglePlaceId(placeId)
                ? google.findForSelection(placeId, sessionToken)
                : geoapify.findById(placeId);
    }

    @Override
    public PlaceDetails findById(String placeId) {
        // Google 키를 뺀 뒤에도 Google 장소는 Google로 보내 PLACE_PROVIDER_UNAVAILABLE로 알린다.
        return isGooglePlaceId(placeId) ? google.findById(placeId) : geoapify.findById(placeId);
    }

    private static boolean isGooglePlaceId(String placeId) {
        return placeId != null && !GEOAPIFY_PLACE_ID.matcher(placeId).matches();
    }
}
