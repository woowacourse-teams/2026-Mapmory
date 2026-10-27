package com.mapmory.backend.travelrecord.mapsummary.service;

import com.mapmory.backend.common.monitoring.MonitoredOperation;
import com.mapmory.backend.common.monitoring.OperationTimer;
import com.mapmory.backend.member.Member;
import com.mapmory.backend.region.application.RegionResolver;
import com.mapmory.backend.region.domain.RegionType;
import com.mapmory.backend.tag.TagService;
import com.mapmory.backend.travelrecord.mapsummary.RegionMapSummary;
import com.mapmory.backend.travelrecord.mapsummary.cache.MapSummaryCache;
import com.mapmory.backend.travelrecord.mapsummary.policy.LevelPolicy;
import com.mapmory.backend.travelrecord.mapsummary.repository.RegionMapSummaryQueryResult;
import com.mapmory.backend.travelrecord.mapsummary.repository.RegionMapSummaryRepository;
import java.util.List;
import org.springframework.stereotype.Service;

@Service
public class RegionMapSummaryService {

    private final RegionResolver regionResolver;
    private final RegionMapSummaryRepository regionMapSummaryRepository;
    private final LevelPolicy levelPolicy;
    private final TagService tagService;
    private final OperationTimer operationTimer;
    private final MapSummaryCache mapSummaryCache;

    public RegionMapSummaryService(
            RegionResolver regionResolver,
            RegionMapSummaryRepository regionMapSummaryRepository,
            LevelPolicy levelPolicy,
            TagService tagService,
            OperationTimer operationTimer,
            MapSummaryCache mapSummaryCache
    ) {
        this.regionResolver = regionResolver;
        this.regionMapSummaryRepository = regionMapSummaryRepository;
        this.levelPolicy = levelPolicy;
        this.tagService = tagService;
        this.operationTimer = operationTimer;
        this.mapSummaryCache = mapSummaryCache;
    }

    /**
     * 캐시에 있으면 DB에 가지 않는다. 트랜잭션도 열지 않아 커넥션을 빌리지 않는다.
     * 캐시에 있다는 건 같은 회원이 같은 지역·태그로 검증을 통과했다는 뜻이다. 지역은 바뀌지 않고,
     * 태그가 삭제되면 그 회원 캐시를 비우므로 검증을 다시 하지 않는다.
     */
    public List<RegionMapSummary> getSummaries(Member member, Long parentRegionId, Long tagId) {
        return mapSummaryCache.get(member.getId(), parentRegionId, tagId,
                () -> loadSummaries(member, parentRegionId, tagId));
    }

    private List<RegionMapSummary> loadSummaries(Member member, Long parentRegionId, Long tagId) {
        validateParentRegion(parentRegionId);
        if (tagId != null) {
            tagService.getOwnedTag(member, tagId);
        }
        List<RegionMapSummaryQueryResult> summaries = operationTimer.record(
                MonitoredOperation.MAP_SUMMARY_QUERY,
                () -> regionMapSummaryRepository.findRegionMapSummaries(member.getId(), parentRegionId, tagId)
        );
        return summaries.stream()
                .map(this::toSummary)
                .toList();
    }

    private RegionMapSummary toSummary(RegionMapSummaryQueryResult result) {
        return new RegionMapSummary(
                result.getRegionId(),
                result.getRegionCode(),
                RegionType.valueOf(result.getRegionType()),
                result.getName(),
                result.getRecordCount(),
                levelPolicy.levelFor(result.getRecordCount())
        );
    }

    private void validateParentRegion(Long parentRegionId) {
        if (parentRegionId != null) {
            regionResolver.validateExists(parentRegionId);
        }
    }
}
