package com.mapmory.backend.travelrecord.mapsummary.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.mapmory.backend.common.exception.BusinessException;
import com.mapmory.backend.common.monitoring.OperationTimer;
import com.mapmory.backend.member.Member;
import com.mapmory.backend.region.domain.RegionRepository;
import com.mapmory.backend.region.domain.RegionType;
import com.mapmory.backend.region.exception.RegionErrorCode;
import com.mapmory.backend.tag.Tag;
import com.mapmory.backend.tag.TagService;
import com.mapmory.backend.travelrecord.mapsummary.RegionMapSummary;
import com.mapmory.backend.travelrecord.mapsummary.cache.MapSummaryCache;
import com.mapmory.backend.travelrecord.mapsummary.cache.MapSummaryCacheProperties;
import com.mapmory.backend.travelrecord.mapsummary.policy.LevelPolicy;
import com.mapmory.backend.travelrecord.mapsummary.policy.MapColorLevel;
import com.mapmory.backend.travelrecord.mapsummary.repository.RegionMapSummaryQueryResult;
import com.mapmory.backend.travelrecord.mapsummary.repository.RegionMapSummaryRepository;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
@DisplayName("Region 지도 요약 서비스")
class RegionMapSummaryServiceTest {

    @Mock
    private Member member;

    @Mock
    private RegionRepository regionRepository;

    @Mock
    private RegionMapSummaryRepository regionMapSummaryRepository;

    @Mock
    private TagService tagService;

    private final LevelPolicy levelPolicy = LevelPolicy.standard();
    private final OperationTimer operationTimer = new OperationTimer(new SimpleMeterRegistry());
    private final MapSummaryCache mapSummaryCache = new MapSummaryCache(
            new MapSummaryCacheProperties(100, Duration.ofMinutes(10)),
            new SimpleMeterRegistry()
    );

    @Nested
    @DisplayName("Region 요약을 조회할 때")
    class GetSummaries {

        @Test
        @DisplayName("부모 ID가 없으면 루트 국가별 요약을 반환한다")
        void returnsRootCountrySummaries() {
            RegionMapSummaryService service = service();
            when(member.getId()).thenReturn(10L);
            when(regionMapSummaryRepository.findRegionMapSummaries(10L, null, null))
                    .thenReturn(List.of(result(1L, "KR", "대한민국", "COUNTRY", 3L)));

            List<RegionMapSummary> responses = service.getSummaries(member, null, null);

            assertThat(responses).containsExactly(new RegionMapSummary(
                    1L,
                    "KR",
                    RegionType.COUNTRY,
                    "대한민국",
                    3L,
                    MapColorLevel.MEDIUM
            ));
            verify(regionRepository, never()).existsById(org.mockito.ArgumentMatchers.anyLong());
        }

        @DisplayName("부모 ID가 있으면 직속 하위 Region 타입의 요약을 반환한다")
        @ParameterizedTest(name = "부모 {0}의 자식 타입은 {1}이다")
        @CsvSource({
                "1, PROVINCE, 15, 49, 제주특별자치도",
                "15, DISTRICT, 150, 50110, 제주시"
        })
        void returnsDirectChildSummaries(
                long parentRegionId,
                String regionType,
                long childRegionId,
                String regionCode,
                String name
        ) {
            RegionMapSummaryService service = service();
            when(member.getId()).thenReturn(10L);
            when(regionRepository.existsById(parentRegionId)).thenReturn(true);
            when(regionMapSummaryRepository.findRegionMapSummaries(10L, parentRegionId, null))
                    .thenReturn(List.of(result(childRegionId, regionCode, name, regionType, 1L)));

            List<RegionMapSummary> responses = service.getSummaries(member, parentRegionId, null);

            assertThat(responses).containsExactly(new RegionMapSummary(
                    childRegionId,
                    regionCode,
                    RegionType.valueOf(regionType),
                    name,
                    1L,
                    MapColorLevel.LOW
            ));
        }

        @Test
        @DisplayName("부모 Region을 찾을 수 없으면 기록을 조회하지 않는다")
        void rejectsUnknownParentRegion() {
            RegionMapSummaryService service = service();
            when(regionRepository.existsById(999L)).thenReturn(false);

            assertThatThrownBy(() -> service.getSummaries(member, 999L, null))
                    .isInstanceOfSatisfying(BusinessException.class, exception ->
                            assertThat(exception.getErrorCode()).isEqualTo(RegionErrorCode.REGION_NOT_FOUND));
            verify(regionMapSummaryRepository, never()).findRegionMapSummaries(10L, 999L, null);
        }

        @Test
        @DisplayName("태그가 있으면 소유권을 확인하고 해당 태그로 요약을 필터링한다")
        void filtersSummariesByOwnedTag() {
            RegionMapSummaryService service = service();
            Tag tag = org.mockito.Mockito.mock(Tag.class);
            when(member.getId()).thenReturn(10L);
            when(tagService.getOwnedTag(member, 7L)).thenReturn(tag);
            when(regionMapSummaryRepository.findRegionMapSummaries(10L, null, 7L))
                    .thenReturn(List.of(result(1L, "KR", "대한민국", "COUNTRY", 1L)));

            List<RegionMapSummary> responses = service.getSummaries(member, null, 7L);

            assertThat(responses).singleElement().extracting(RegionMapSummary::recordCount).isEqualTo(1L);
            verify(tagService).getOwnedTag(member, 7L);
        }
    }

    @Nested
    @DisplayName("캐시가 있을 때")
    class Cached {

        @Test
        @DisplayName("같은 회원이 같은 조건으로 다시 부르면 DB에 가지 않는다")
        void secondCallSkipsDatabase() {
            RegionMapSummaryService service = service();
            when(member.getId()).thenReturn(10L);
            when(regionMapSummaryRepository.findRegionMapSummaries(10L, null, null))
                    .thenReturn(List.of(result(1L, "KR", "대한민국", "COUNTRY", 3L)));

            List<RegionMapSummary> first = service.getSummaries(member, null, null);
            List<RegionMapSummary> second = service.getSummaries(member, null, null);

            assertThat(second).isEqualTo(first);
            verify(regionMapSummaryRepository, times(1)).findRegionMapSummaries(10L, null, null);
        }

        @Test
        @DisplayName("회원 캐시를 비우면 다음 조회는 DB에서 다시 읽는다")
        void evictedMemberReloads() {
            RegionMapSummaryService service = service();
            when(member.getId()).thenReturn(10L);
            when(regionMapSummaryRepository.findRegionMapSummaries(10L, null, null))
                    .thenReturn(List.of(result(1L, "KR", "대한민국", "COUNTRY", 3L)))
                    .thenReturn(List.of(result(1L, "KR", "대한민국", "COUNTRY", 4L)));

            service.getSummaries(member, null, null);
            mapSummaryCache.evictMember(10L);
            List<RegionMapSummary> reloaded = service.getSummaries(member, null, null);

            assertThat(reloaded).singleElement().extracting(RegionMapSummary::recordCount).isEqualTo(4L);
            verify(regionMapSummaryRepository, times(2)).findRegionMapSummaries(10L, null, null);
        }
    }

    private RegionMapSummaryService service() {
        return new RegionMapSummaryService(
                regionRepository,
                regionMapSummaryRepository,
                levelPolicy,
                tagService,
                operationTimer,
                mapSummaryCache
        );
    }

    private static RegionMapSummaryQueryResult result(
            Long regionId,
            String regionCode,
            String name,
            String regionType,
            long recordCount
    ) {
        return new RegionMapSummaryQueryResult() {
            @Override
            public Long getRegionId() {
                return regionId;
            }

            @Override
            public String getRegionCode() {
                return regionCode;
            }

            @Override
            public String getName() {
                return name;
            }

            @Override
            public String getRegionType() {
                return regionType;
            }

            @Override
            public long getRecordCount() {
                return recordCount;
            }
        };
    }
}
