package com.mapmory.backend.place.web;

import static org.assertj.core.api.Assertions.assertThat;

import com.mapmory.backend.IntegrationTest;
import com.mapmory.backend.place.application.model.PlaceDetails;
import com.mapmory.backend.place.application.model.SelectedPlace;
import com.mapmory.backend.place.web.dto.PlaceSelectionResponse;
import com.mapmory.backend.region.Region;
import com.mapmory.backend.region.RegionResolver;
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
                new PlaceDetails("ChIJ-park", null, "KR", null, null, List.of("서울특별시", "영등포구")), district);

        PlaceSelectionResponse response = PlaceSelectionResponse.from(selected);

        assertThat(response.suggestedRegion().country().code()).isEqualTo("KR");
        assertThat(response.suggestedRegion().province().code()).isEqualTo("11");
        assertThat(response.suggestedRegion().district().code()).isEqualTo("11560");
    }
}
