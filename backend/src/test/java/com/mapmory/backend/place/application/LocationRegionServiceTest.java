package com.mapmory.backend.place.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import com.mapmory.backend.place.application.port.ReverseGeocodePort;
import com.mapmory.backend.place.domain.Coordinate;
import com.mapmory.backend.place.domain.PlaceProvider;
import com.mapmory.backend.region.application.RegionSuggester;
import com.mapmory.backend.region.domain.Region;
import com.mapmory.backend.region.domain.RegionAddress;
import com.mapmory.backend.region.domain.RegionType;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class LocationRegionServiceTest {

    private static final PlaceProvider GOOGLE =
            new PlaceProvider("GOOGLE", "Google Maps", "https://www.google.com/maps");
    private static final Coordinate DAEGU_JUNG_GU = new Coordinate(35.8694, 128.6062);

    @Mock ReverseGeocodePort reverseGeocodePort;
    @Mock RegionSuggester regionSuggester;
    @InjectMocks LocationRegionService service;

    @BeforeEach
    void setUp() {
        when(reverseGeocodePort.provider()).thenReturn(GOOGLE);
    }

    @Test
    void 좌표의_주소로_구_군까지_추천한다() {
        RegionAddress address = new RegionAddress("KR", "대구광역시", List.of("중구"));
        Region district = Region.of(null, null, "27110", "중구", RegionType.DISTRICT);
        when(reverseGeocodePort.findAddress(DAEGU_JUNG_GU)).thenReturn(Optional.of(address));
        when(regionSuggester.suggest(address)).thenReturn(Optional.of(district));

        LocatedRegion result = service.locate(DAEGU_JUNG_GU);

        assertThat(result.suggestedRegion()).isEqualTo(district);
        assertThat(result.manualRegionRequired()).isFalse();
        assertThat(result.countryCode()).isEqualTo("KR");
        assertThat(result.provider()).isEqualTo(GOOGLE);
    }

    @Test
    void 주소가_없는_좌표는_직접_고르도록_알린다() {
        when(reverseGeocodePort.findAddress(DAEGU_JUNG_GU)).thenReturn(Optional.empty());
        when(regionSuggester.suggest(RegionAddress.UNKNOWN)).thenReturn(Optional.empty());

        LocatedRegion result = service.locate(DAEGU_JUNG_GU);

        assertThat(result.suggestedRegion()).isNull();
        assertThat(result.manualRegionRequired()).isTrue();
        assertThat(result.countryCode()).isNull();
    }
}
