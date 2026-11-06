package com.mapmory.backend.place.infrastructure.google.places;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;

class GooglePlacesPropertiesTest {

    // 엔드포인트를 enum에만 더하고 yaml에 월 한도를 빠뜨리면, 첫 호출이 아니라 기동할 때 어느 키인지 알려 주며 실패한다.
    @Test
    void 월_한도가_빠진_엔드포인트가_있으면_그_키를_알려_주며_실패한다() {
        assertThatThrownBy(() -> new GooglePlacesProperties.RateLimit(60,
                Map.of(GooglePlacesEndpoint.AUTOCOMPLETE, 20_000), 9_000))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("google.places.rate-limit.per-month.details");
    }

    @Test
    void 월_한도가_1보다_작으면_실패한다() {
        assertThatThrownBy(() -> new GooglePlacesProperties.RateLimit(60,
                Map.of(GooglePlacesEndpoint.AUTOCOMPLETE, 0, GooglePlacesEndpoint.DETAILS, 20_000), 9_000))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("per-month.autocomplete");
    }

    // yaml에 키만 있고 값이 비면(예: details: ${LIMIT:}) null로 묶인다. NPE가 아니라 같은 안내로 실패한다.
    @Test
    void 월_한도_값이_비어_있으면_그_키를_알려_주며_실패한다() {
        Map<GooglePlacesEndpoint, Integer> perMonth = new HashMap<>();
        perMonth.put(GooglePlacesEndpoint.AUTOCOMPLETE, 20_000);
        perMonth.put(GooglePlacesEndpoint.DETAILS, null);

        assertThatThrownBy(() -> new GooglePlacesProperties.RateLimit(60, perMonth, 9_000))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("per-month.details");
    }
}
