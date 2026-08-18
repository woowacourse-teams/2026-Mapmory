package com.mapmory.backend.travelrecord.mapsummary.controller;

import com.mapmory.backend.auth.security.LoginMemberId;
import com.mapmory.backend.common.dto.ApiResponse;
import com.mapmory.backend.travelrecord.mapsummary.dto.RegionMapSummaryResponse;
import com.mapmory.backend.travelrecord.mapsummary.service.RegionMapSummaryService;
import jakarta.validation.constraints.Positive;
import java.util.List;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Validated
@RestController
@RequestMapping("/api/v1/travel-records/map-summary/regions")
public class RegionMapSummaryController {

    private final RegionMapSummaryService regionMapSummaryService;

    public RegionMapSummaryController(RegionMapSummaryService regionMapSummaryService) {
        this.regionMapSummaryService = regionMapSummaryService;
    }

    @GetMapping("/roots")
    public ApiResponse<List<RegionMapSummaryResponse>> getRootSummaries(@LoginMemberId Long memberId) {
        return ApiResponse.from(regionMapSummaryService.getSummaries(memberId, null));
    }

    @GetMapping("/{regionId}/children")
    public ApiResponse<List<RegionMapSummaryResponse>> getChildSummaries(
            @LoginMemberId Long memberId,
            @PathVariable
            @Positive(message = "지역 ID는 양수여야 합니다.")
            Long regionId
    ) {
        return ApiResponse.from(regionMapSummaryService.getSummaries(memberId, regionId));
    }
}
