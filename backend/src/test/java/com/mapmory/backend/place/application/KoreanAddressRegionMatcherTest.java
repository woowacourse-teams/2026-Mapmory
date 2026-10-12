package com.mapmory.backend.place.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import com.mapmory.backend.region.Region;
import com.mapmory.backend.region.RegionResolver;
import com.mapmory.backend.region.RegionType;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class KoreanAddressRegionMatcherTest {

    @Mock RegionResolver regionResolver;

    private final Region korea = Region.of(null, null, "KR", "대한민국", RegionType.COUNTRY);
    private final Region seoul = Region.of(korea, korea, "11", "서울특별시", RegionType.PROVINCE);
    private final Region gwangju = Region.of(korea, korea, "29", "광주광역시", RegionType.PROVINCE);
    private final Region gyeonggi = Region.of(korea, korea, "41", "경기도", RegionType.PROVINCE);
    private final Region gangwon = Region.of(korea, korea, "42", "강원특별자치도", RegionType.PROVINCE);
    private final Region sejong = Region.of(korea, korea, "50", "세종특별자치시", RegionType.PROVINCE);
    private final Region jongno = Region.of(seoul, korea, "11110", "종로구", RegionType.DISTRICT);
    private final Region gwangjuDonggu = Region.of(gwangju, korea, "29110", "동구", RegionType.DISTRICT);
    private final Region seongnam = Region.of(gyeonggi, korea, "41130", "성남시", RegionType.DISTRICT);
    private final Region gyeonggiGwangju = Region.of(gyeonggi, korea, "41610", "광주시", RegionType.DISTRICT);
    private final Region yangyang = Region.of(gangwon, korea, "42830", "양양군", RegionType.DISTRICT);
    private final Region sejongCity = Region.of(sejong, korea, "36110", "세종시", RegionType.DISTRICT);

    private KoreanAddressRegionMatcher matcher;

    @BeforeEach
    void setUp() {
        matcher = new KoreanAddressRegionMatcher(regionResolver);
        when(regionResolver.resolve("KR", null, null)).thenReturn(korea);
        when(regionResolver.children(korea, RegionType.PROVINCE))
                .thenReturn(List.of(seoul, gwangju, gyeonggi, gangwon, sejong));
        when(regionResolver.children(seoul, RegionType.DISTRICT)).thenReturn(List.of(jongno));
        when(regionResolver.children(gwangju, RegionType.DISTRICT)).thenReturn(List.of(gwangjuDonggu));
        when(regionResolver.children(gyeonggi, RegionType.DISTRICT)).thenReturn(List.of(seongnam, gyeonggiGwangju));
        when(regionResolver.children(gangwon, RegionType.DISTRICT)).thenReturn(List.of(yangyang));
        when(regionResolver.children(sejong, RegionType.DISTRICT)).thenReturn(List.of(sejongCity));
    }

    @Test
    void 서울의_구를_찾는다() {
        assertThat(matcher.match(List.of("서울특별시", "종로구"))).contains(jongno);
    }

    @Test
    void 일반구가_있는_시는_시_단위로_찾는다() {
        assertThat(matcher.match(List.of("경기도", "성남시", "분당구"))).contains(seongnam);
    }

    @Test
    void 시군구_이름이_다른_시도와_겹쳐도_주소의_시도_안에서_찾는다() {
        assertThat(matcher.match(List.of("경기도", "광주시"))).contains(gyeonggiGwangju);
    }

    @Test
    void 시도_이름이_예전_명칭이어도_찾는다() {
        assertThat(matcher.match(List.of("강원도", "양양군"))).contains(yangyang);
    }

    @Test
    void 시군구가_하나뿐인_세종은_시도만으로_찾는다() {
        assertThat(matcher.match(List.of("세종특별자치시"))).contains(sejongCity);
    }

    @Test
    void 시군구를_찾지_못하면_직접_선택하도록_빈_값을_준다() {
        assertThat(matcher.match(List.of("경기도", "없는시"))).isEmpty();
        assertThat(matcher.match(List.of())).isEmpty();
    }
}
