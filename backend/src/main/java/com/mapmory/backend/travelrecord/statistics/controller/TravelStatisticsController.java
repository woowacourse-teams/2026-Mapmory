package com.mapmory.backend.travelrecord.statistics.controller;

import com.mapmory.backend.auth.security.LoginMember;
import com.mapmory.backend.common.dto.ApiResponse;
import com.mapmory.backend.member.Member;
import com.mapmory.backend.travelrecord.statistics.dto.TravelStatisticsResponse;
import com.mapmory.backend.travelrecord.statistics.model.TravelStatistics;
import com.mapmory.backend.travelrecord.statistics.service.TravelStatisticsService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/travel-records/statistics")
public class TravelStatisticsController {

    private final TravelStatisticsService travelStatisticsService;

    public TravelStatisticsController(TravelStatisticsService travelStatisticsService) {
        this.travelStatisticsService = travelStatisticsService;
    }

    @GetMapping
    public ApiResponse<TravelStatisticsResponse> getStatistics(@LoginMember Member member) {
        TravelStatistics statistics = travelStatisticsService.getStatistics(member);
        return ApiResponse.from(TravelStatisticsResponse.from(statistics));
    }
}
