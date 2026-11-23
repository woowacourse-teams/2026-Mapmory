package com.mapmory.backend.place.application;

import static org.assertj.core.api.Assertions.assertThat;

import com.mapmory.backend.place.domain.PlaceDetails;
import com.mapmory.backend.place.domain.PlaceId;
import com.mapmory.backend.place.domain.PlaceProvider;
import com.mapmory.backend.region.domain.Region;
import com.mapmory.backend.region.domain.RegionAddress;
import com.mapmory.backend.region.domain.RegionType;
import java.util.List;
import org.junit.jupiter.api.Test;

class SelectedPlaceTest {

    private static final PlaceProvider GOOGLE = new PlaceProvider("GOOGLE", "Google Maps", "https://www.google.com/maps");

    private final Region korea = Region.of(null, null, "KR", "대한민국", RegionType.COUNTRY);
    private final Region gyeongbuk = Region.of(korea, korea, "47", "경상북도", RegionType.PROVINCE);
    private final Region ulleung = Region.of(gyeongbuk, korea, "47940", "울릉군", RegionType.DISTRICT);

    @Test
    void 제공자가_준_국가_코드를_쓴다() {
        SelectedPlace selected = new SelectedPlace(place(new RegionAddress("GU", null, List.of())), null);

        assertThat(selected.countryCode()).isEqualTo("GU");
    }

    @Test
    void 제공자가_국가를_주지_않아도_찾은_지역의_국가를_쓴다() {
        SelectedPlace selected = new SelectedPlace(
                place(new RegionAddress(null, null, List.of("울릉군", "울릉읍", "독도리"))), ulleung);

        assertThat(selected.countryCode()).isEqualTo("KR");
    }

    @Test
    void 제공자가_국가를_주지_않고_지역도_못_찾으면_비어_있다() {
        SelectedPlace selected = new SelectedPlace(place(RegionAddress.UNKNOWN), null);

        assertThat(selected.countryCode()).isNull();
    }

    private static PlaceDetails place(RegionAddress address) {
        return new PlaceDetails(PlaceId.from("ChIJ-test"), null, address, GOOGLE);
    }
}
