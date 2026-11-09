package com.mapmory.backend.place.web;

import static org.assertj.core.api.Assertions.assertThat;

import com.mapmory.backend.IntegrationTest;
import com.mapmory.backend.place.application.SelectedPlace;
import com.mapmory.backend.place.domain.PlaceDetails;
import com.mapmory.backend.place.domain.PlaceId;
import com.mapmory.backend.place.domain.PlaceProvider;
import com.mapmory.backend.place.web.dto.PlaceSelectionResponse;
import com.mapmory.backend.region.application.RegionResolver;
import com.mapmory.backend.region.domain.Region;
import com.mapmory.backend.region.domain.RegionAddress;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

class PlaceSelectionResponseIntegrationTest extends IntegrationTest {

    @Autowired
    private RegionResolver regionResolver;

    @Test
    void 트랜잭션이_없는_API_계층에서도_추천_지역_계층을_응답한다() {
        Region district = regionResolver.resolve("KR", "11", "11560");
        SelectedPlace selected = new SelectedPlace(
                new PlaceDetails(PlaceId.from("ChIJ-park"), null, new RegionAddress("KR", "서울특별시", List.of("영등포구")),
                        new PlaceProvider("GOOGLE", "Google Maps", "https://www.google.com/maps")), district);

        PlaceSelectionResponse response = PlaceSelectionResponse.from(selected);

        assertThat(response.suggestedRegion().country().code()).isEqualTo("KR");
        assertThat(response.suggestedRegion().province().code()).isEqualTo("11");
        assertThat(response.suggestedRegion().district().code()).isEqualTo("11560");
    }

    @Test
    void 제공자가_국가를_주지_않아도_찾은_지역의_국가를_응답한다() {
        Region ulleung = regionResolver.resolve("KR", "47", "47940");
        SelectedPlace selected = new SelectedPlace(
                new PlaceDetails(PlaceId.from("ChIJ-dokdo"), null, new RegionAddress(null, null, List.of("울릉군", "울릉읍", "독도리")),
                        new PlaceProvider("GOOGLE", "Google Maps", "https://www.google.com/maps")), ulleung);

        PlaceSelectionResponse response = PlaceSelectionResponse.from(selected);

        assertThat(response.countryCode()).isEqualTo("KR");
        assertThat(response.suggestedRegion().district().code()).isEqualTo("47940");
    }

    @Test
    void 제공자가_국가를_주지_않고_지역도_못_찾으면_국가는_비어_있다() {
        SelectedPlace selected = new SelectedPlace(
                new PlaceDetails(PlaceId.from("ChIJ-rocks"), null, RegionAddress.UNKNOWN,
                        new PlaceProvider("GOOGLE", "Google Maps", "https://www.google.com/maps")), null);

        assertThat(PlaceSelectionResponse.from(selected).countryCode()).isNull();
    }
}
