package com.mapmory.backend.place.domain;

import static org.assertj.core.api.Assertions.assertThat;

import com.mapmory.backend.region.RegionAddress;
import org.junit.jupiter.api.Test;

class PlaceDetailsTest {

    private static final PlaceProvider GOOGLE =
            new PlaceProvider("GOOGLE", "Google Maps", "https://www.google.com/maps");

    @Test
    void 조회하지_않은_장소는_ID와_출처만_가진다() {
        PlaceDetails place = PlaceDetails.withoutLookup(PlaceId.from("ChIJ-bundang"), GOOGLE);

        assertThat(place.id()).isEqualTo(PlaceId.from("ChIJ-bundang"));
        assertThat(place.name()).isNull();
        assertThat(place.address()).isEqualTo(RegionAddress.UNKNOWN);
        assertThat(place.provider()).isEqualTo(GOOGLE);
    }

    @Test
    void 주소가_없으면_모르는_주소로_둔다() {
        PlaceDetails place = new PlaceDetails(PlaceId.from("ChIJ-island"), null, null, GOOGLE);

        assertThat(place.address()).isEqualTo(RegionAddress.UNKNOWN);
    }
}
