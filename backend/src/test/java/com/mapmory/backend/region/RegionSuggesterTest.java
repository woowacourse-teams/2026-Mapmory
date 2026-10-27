package com.mapmory.backend.region;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.mapmory.backend.common.exception.BusinessException;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class RegionSuggesterTest {

    @Mock RegionResolver regionResolver;
    @Mock KoreanAddressRegionMatcher addressRegionMatcher;
    @InjectMocks RegionSuggester suggester;

    @Test
    void 대한민국은_주소_이름으로_시군구를_추천한다() {
        Region country = Region.of(null, null, "KR", "대한민국", RegionType.COUNTRY);
        Region province = Region.of(country, country, "11", "서울특별시", RegionType.PROVINCE);
        Region district = Region.of(province, country, "11110", "종로구", RegionType.DISTRICT);
        when(addressRegionMatcher.match(List.of("서울특별시", "종로구"))).thenReturn(Optional.of(district));

        assertThat(suggester.suggest("KR", List.of("서울특별시", "종로구"))).contains(district);
    }

    @Test
    void 주소로_시군구를_찾지_못하면_빈_값이다() {
        when(addressRegionMatcher.match(List.of())).thenReturn(Optional.empty());

        assertThat(suggester.suggest("KR", List.of())).isEmpty();
    }

    @Test
    void 해외는_국가를_추천한다() {
        Region japan = Region.of(null, null, "JP", "일본", RegionType.COUNTRY);
        when(regionResolver.resolveCountry("JP")).thenReturn(japan);

        assertThat(suggester.suggest("JP", List.of())).contains(japan);
        verifyNoInteractions(addressRegionMatcher);
    }

    @Test
    void DB에_없는_국가는_빈_값이다() {
        when(regionResolver.resolveCountry("ZZ"))
                .thenThrow(new BusinessException(RegionErrorCode.REGION_NOT_FOUND));

        assertThat(suggester.suggest("ZZ", List.of())).isEmpty();
    }

    @Test
    void 국가_코드가_없으면_빈_값이다() {
        assertThat(suggester.suggest(null, List.of())).isEmpty();
        verifyNoInteractions(regionResolver, addressRegionMatcher);
    }
}
