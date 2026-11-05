package com.mapmory.backend.region.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.mapmory.backend.common.exception.BusinessException;
import com.mapmory.backend.region.domain.RegionBoundary;
import com.mapmory.backend.region.domain.RegionRepository;
import com.mapmory.backend.region.domain.RegionType;
import com.mapmory.backend.region.exception.RegionErrorCode;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class RegionBoundaryServiceTest {

    private final RegionRepository regionRepository = mock(RegionRepository.class);
    private final RegionBoundaryReader reader = mock(RegionBoundaryReader.class);
    private final RegionBoundaryService service = new RegionBoundaryService(regionRepository, reader);

    @Test
    void 지역_목록에_있는_나라의_경계를_준다() {
        RegionBoundary boundary = new RegionBoundary("HK", "v1", List.of(List.of(List.of(114.0, 22.0))));
        when(regionRepository.existsByRegionTypeAndRegionCode(RegionType.COUNTRY, "HK")).thenReturn(true);
        when(reader.read("HK")).thenReturn(Optional.of(boundary));

        assertThat(service.countryBoundary("HK")).isEqualTo(boundary);
    }

    @Test
    void 지역_목록에_없는_나라는_경계_파일을_읽지_않는다() {
        when(regionRepository.existsByRegionTypeAndRegionCode(RegionType.COUNTRY, "XK")).thenReturn(false);

        assertThatThrownBy(() -> service.countryBoundary("XK"))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(RegionErrorCode.REGION_NOT_FOUND);
        verifyNoInteractions(reader);
    }

    @Test
    void 경계_데이터가_없으면_따로_알린다() {
        when(regionRepository.existsByRegionTypeAndRegionCode(RegionType.COUNTRY, "UM")).thenReturn(true);
        when(reader.read("UM")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.countryBoundary("UM"))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(RegionErrorCode.REGION_BOUNDARY_NOT_FOUND);
    }
}
