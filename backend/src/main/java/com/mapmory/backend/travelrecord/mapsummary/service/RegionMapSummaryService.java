package com.mapmory.backend.travelrecord.mapsummary.service;

import com.mapmory.backend.common.exception.BusinessException;
import com.mapmory.backend.common.monitoring.MonitoredOperation;
import com.mapmory.backend.common.monitoring.OperationTimer;
import com.mapmory.backend.member.Member;
import com.mapmory.backend.region.exception.RegionErrorCode;
import com.mapmory.backend.region.RegionRepository;
import com.mapmory.backend.tag.TagService;
import com.mapmory.backend.region.RegionType;
import com.mapmory.backend.travelrecord.mapsummary.RegionMapSummary;
import com.mapmory.backend.travelrecord.mapsummary.policy.LevelPolicy;
import com.mapmory.backend.travelrecord.mapsummary.repository.RegionMapSummaryQueryResult;
import com.mapmory.backend.travelrecord.mapsummary.repository.RegionMapSummaryRepository;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class RegionMapSummaryService {

    private final RegionRepository regionRepository;
    private final RegionMapSummaryRepository regionMapSummaryRepository;
    private final LevelPolicy levelPolicy;
    private final TagService tagService;
    private final OperationTimer operationTimer;

    public RegionMapSummaryService(
            RegionRepository regionRepository,
            RegionMapSummaryRepository regionMapSummaryRepository,
            LevelPolicy levelPolicy,
            TagService tagService,
            OperationTimer operationTimer
    ) {
        this.regionRepository = regionRepository;
        this.regionMapSummaryRepository = regionMapSummaryRepository;
        this.levelPolicy = levelPolicy;
        this.tagService = tagService;
        this.operationTimer = operationTimer;
    }

    @Transactional(readOnly = true)
    public List<RegionMapSummary> getSummaries(Member member, Long parentRegionId, Long tagId) {
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
        if (parentRegionId != null && !regionRepository.existsById(parentRegionId)) {
            throw new BusinessException(RegionErrorCode.REGION_NOT_FOUND);
        }
    }
}
