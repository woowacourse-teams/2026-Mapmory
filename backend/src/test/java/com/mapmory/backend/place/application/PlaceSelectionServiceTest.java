package com.mapmory.backend.place.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.mapmory.backend.common.exception.BusinessException;
import com.mapmory.backend.place.application.PlaceErrorCode;
import com.mapmory.backend.place.application.model.PlaceDetails;
import com.mapmory.backend.place.application.model.SelectedPlace;
import com.mapmory.backend.place.application.port.DistrictLocator;
import com.mapmory.backend.place.application.port.PlaceLookupPort;
import com.mapmory.backend.place.application.port.PlaceRateLimitPort;
import com.mapmory.backend.region.Region;
import com.mapmory.backend.region.RegionResolver;
import com.mapmory.backend.region.RegionType;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class PlaceSelectionServiceTest {

    @Mock PlaceLookupPort placeLookupPort;
    @Mock PlaceRateLimitPort rateLimitPort;
    @Mock DistrictLocator districtLocator;
    @Mock RegionResolver regionResolver;
    @InjectMocks PlaceSelectionService service;

    @Test
    void 장소_좌표가_시군구에_포함되면_지역을_추천한다() {
        Region country = Region.of(null, null, "KR", "대한민국", RegionType.COUNTRY);
        Region province = Region.of(country, country, "11", "서울특별시", RegionType.PROVINCE);
        Region district = Region.of(province, country, "11560", "영등포구", RegionType.DISTRICT);
        when(placeLookupPort.findById("park-1"))
                .thenReturn(new PlaceDetails("park-1", "여의도한강공원", "KR", 37.528, 126.932, null, null));
        when(districtLocator.find(126.932, 37.528))
                .thenReturn(Optional.of(new DistrictLocator.DistrictMatch("11", "11560")));
        when(regionResolver.resolve("KR", "11", "11560")).thenReturn(district);

        SelectedPlace result = service.select(1L, "park-1");

        assertThat(result.suggestedRegion()).isEqualTo(district);
    }

    @Test
    void 경계에서_지역을_찾지_못하면_직접_선택하도록_알린다() {
        when(placeLookupPort.findById("park-1"))
                .thenReturn(new PlaceDetails("park-1", "한강공원", "KR", 37.5, 127.0, null, null));
        when(districtLocator.find(127.0, 37.5)).thenReturn(Optional.empty());

        SelectedPlace result = service.select(1L, "park-1");

        assertThat(result.suggestedRegion()).isNull();
    }

    @Test
    void 제공자가_국가_코드를_주지_않으면_직접_선택하도록_알린다() {
        when(placeLookupPort.findById("place-1"))
                .thenReturn(new PlaceDetails("place-1", "섬", null, 0.0, 0.0, null, null));

        SelectedPlace result = service.select(1L, "place-1");

        assertThat(result.suggestedRegion()).isNull();
    }

    @Test
    void 선택_한도를_넘으면_제공자를_호출하지_않는다() {
        doThrow(new BusinessException(PlaceErrorCode.PLACE_RATE_LIMITED))
                .when(rateLimitPort).checkSelection(1L);

        assertThatThrownBy(() -> service.select(1L, "park-1"))
                .isInstanceOf(BusinessException.class);
        verifyNoInteractions(placeLookupPort);
    }
}
