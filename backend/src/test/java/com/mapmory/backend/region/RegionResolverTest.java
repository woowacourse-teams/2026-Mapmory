package com.mapmory.backend.region;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.mapmory.backend.common.exception.BusinessException;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class RegionResolverTest {

    @Mock
    private RegionRepository regionRepository;

    @InjectMocks
    private RegionResolver regionResolver;

    @Test
    void findsCountry() {
        Region korea = mock(Region.class);
        when(regionRepository.findByParentIsNullAndRegionTypeAndRegionCode(RegionType.COUNTRY, "KR"))
                .thenReturn(Optional.of(korea));

        assertThat(regionResolver.resolve("KR", null, null)).isEqualTo(korea);
    }

    @Test
    void rejectsNonexistentCountry() {
        when(regionRepository.findByParentIsNullAndRegionTypeAndRegionCode(RegionType.COUNTRY, "ZZ"))
                .thenReturn(Optional.empty());

        assertError(() -> regionResolver.resolve("ZZ", null, null), "REGION_NOT_FOUND");
    }

    @Test
    void resolvesProvinceByItsDirectParent() {
        Region korea = parent(1L);
        Region jeju = mock(Region.class);
        when(regionRepository.findByParentIsNullAndRegionTypeAndRegionCode(RegionType.COUNTRY, "KR"))
                .thenReturn(Optional.of(korea));
        when(regionRepository.findByParentIdAndRegionTypeAndRegionCode(1L, RegionType.PROVINCE, "49"))
                .thenReturn(Optional.of(jeju));

        assertThat(regionResolver.resolve("KR", "49", null)).isEqualTo(jeju);
    }

    @Test
    void distinguishesMissingChildFromInvalidHierarchy() {
        Region korea = parent(1L);
        when(regionRepository.findByParentIsNullAndRegionTypeAndRegionCode(RegionType.COUNTRY, "KR"))
                .thenReturn(Optional.of(korea));
        when(regionRepository.findByParentIdAndRegionTypeAndRegionCode(1L, RegionType.PROVINCE, "99"))
                .thenReturn(Optional.empty());
        when(regionRepository.existsByRegionTypeAndRegionCode(RegionType.PROVINCE, "99")).thenReturn(false);

        assertError(() -> regionResolver.resolve("KR", "99", null), "REGION_NOT_FOUND");

        when(regionRepository.findByParentIdAndRegionTypeAndRegionCode(1L, RegionType.PROVINCE, "49"))
                .thenReturn(Optional.empty());
        when(regionRepository.existsByRegionTypeAndRegionCode(RegionType.PROVINCE, "49")).thenReturn(true);

        assertError(() -> regionResolver.resolve("KR", "49", null), "INVALID_REGION_HIERARCHY");
    }

    @Test
    void resolvesDistrictByItsDirectParent() {
        Region korea = parent(1L);
        Region jeju = parent(2L);
        Region jejuCity = mock(Region.class);
        when(regionRepository.findByParentIsNullAndRegionTypeAndRegionCode(RegionType.COUNTRY, "KR"))
                .thenReturn(Optional.of(korea));
        when(regionRepository.findByParentIdAndRegionTypeAndRegionCode(1L, RegionType.PROVINCE, "49"))
                .thenReturn(Optional.of(jeju));
        when(regionRepository.findByParentIdAndRegionTypeAndRegionCode(2L, RegionType.DISTRICT, "50110"))
                .thenReturn(Optional.of(jejuCity));

        assertThat(regionResolver.resolve("KR", "49", "50110")).isEqualTo(jejuCity);
    }

    @Test
    void 상위_지역_코드_없이_하위_지역_코드만_오면_거부한다() {
        assertError(() -> regionResolver.resolve(null, "26", null), "INVALID_REGION_HIERARCHY");
        assertError(() -> regionResolver.resolve("KR", null, "26350"), "INVALID_REGION_HIERARCHY");
    }

    private Region parent(Long id) {
        Region region = mock(Region.class);
        when(region.getId()).thenReturn(id);
        return region;
    }

    private void assertError(Runnable action, String errorCode) {
        assertThatThrownBy(action::run)
                .isInstanceOf(BusinessException.class)
                .extracting(exception -> ((BusinessException) exception).getErrorCode().code())
                .isEqualTo(errorCode);
    }
}
