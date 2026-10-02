package com.mapmory.backend.travelrecord;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import java.util.Objects;

/** 여행 기록에 저장한 장소의 식별자와 표시 정보. */
@Embeddable
public record RecordedPlace(
        @Column(name = "place_provider", length = 20)
        String provider,
        @Column(name = "place_id", length = 255)
        String id,
        @Column(name = "place_name", length = 500)
        String name,
        @Column(name = "place_attribution", length = 255)
        String attribution,
        @Column(name = "place_attribution_url", length = 500)
        String attributionUrl
) {
    public RecordedPlace {
        Objects.requireNonNull(provider, "장소 제공자는 필수입니다.");
        Objects.requireNonNull(id, "장소 ID는 필수입니다.");
        Objects.requireNonNull(name, "장소 이름은 필수입니다.");
    }
}
