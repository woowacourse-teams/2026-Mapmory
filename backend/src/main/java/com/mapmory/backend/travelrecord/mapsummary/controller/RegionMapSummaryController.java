package com.mapmory.backend.travelrecord.mapsummary.controller;

import com.mapmory.backend.auth.security.LoginMember;
import com.mapmory.backend.common.dto.ApiResponse;
import com.mapmory.backend.member.Member;
import com.mapmory.backend.travelrecord.mapsummary.dto.RegionMapSummaryResponse;
import com.mapmory.backend.travelrecord.mapsummary.service.RegionMapSummaryService;
import jakarta.validation.constraints.Positive;
import java.util.List;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
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
    public ApiResponse<List<RegionMapSummaryResponse>> getRootSummaries(
            @LoginMember Member member,
            @RequestParam(required = false) @Positive Long tagId
    ) {
        return ApiResponse.from(RegionMapSummaryResponse.from(
                regionMapSummaryService.getSummaries(member, null, tagId)
        ));
    }

    @GetMapping("/{regionId}/children")
    public ApiResponse<List<RegionMapSummaryResponse>> getChildSummaries(
            @LoginMember Member member,
            @PathVariable
            @Positive(message = "지역 ID는 양수여야 합니다.")
            Long regionId,
            @RequestParam(required = false) @Positive Long tagId
    ) {
        return ApiResponse.from(RegionMapSummaryResponse.from(
                regionMapSummaryService.getSummaries(member, regionId, tagId)
        ));
    }
}
