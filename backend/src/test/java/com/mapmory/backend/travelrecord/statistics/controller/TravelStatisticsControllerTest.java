package com.mapmory.backend.travelrecord.statistics.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.same;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.mapmory.backend.auth.jwt.JwtConfig;
import com.mapmory.backend.auth.jwt.JwtProvider;
import com.mapmory.backend.common.ProblemDetailFactory;
import com.mapmory.backend.member.Member;
import com.mapmory.backend.member.MemberRepository;
import com.mapmory.backend.region.RegionType;
import com.mapmory.backend.travelrecord.statistics.model.TopRegionStatistics;
import com.mapmory.backend.travelrecord.statistics.model.TravelStatistics;
import com.mapmory.backend.travelrecord.statistics.service.TravelStatisticsService;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(TravelStatisticsController.class)
@Import({JwtConfig.class, JwtProvider.class, ProblemDetailFactory.class})
@DisplayName("여행 통계 API")
class TravelStatisticsControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JwtProvider jwtProvider;

    @MockitoBean
    private MemberRepository memberRepository;

    @MockitoBean
    private TravelStatisticsService travelStatisticsService;

    private Long memberId;
    private Member member;

    @BeforeEach
    void setUpMember() {
        memberId = 10L;
        member = Member.of("통계 테스트 회원", UUID.randomUUID());
        when(memberRepository.findById(memberId)).thenReturn(Optional.of(member));
    }

    @Test
    @DisplayName("현재 회원의 전체 여행 통계를 반환한다")
    void returnsTravelStatistics() throws Exception {
        when(travelStatisticsService.getStatistics(any(Member.class))).thenReturn(new TravelStatistics(
                24L,
                138L,
                3L,
                8L,
                List.of("JP", "KR", "US"),
                List.of(new TopRegionStatistics(
                        10L,
                        "11",
                        RegionType.PROVINCE,
                        "서울특별시",
                        7L
                ))
        ));

        mockMvc.perform(get("/api/v1/travel-records/statistics")
                        .header(HttpHeaders.AUTHORIZATION, bearer(memberId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.recordCount").value(24))
                .andExpect(jsonPath("$.data.mediaCount").value(138))
                .andExpect(jsonPath("$.data.visitedCountryCount").value(3))
                .andExpect(jsonPath("$.data.visitedKoreaDistrictCount").value(8))
                .andExpect(jsonPath("$.data.visitedCountryCodes[0]").value("JP"))
                .andExpect(jsonPath("$.data.topRegions[0].regionId").value(10))
                .andExpect(jsonPath("$.data.topRegions[0].code").value("11"))
                .andExpect(jsonPath("$.data.topRegions[0].regionType").value("PROVINCE"))
                .andExpect(jsonPath("$.data.topRegions[0].name").value("서울특별시"))
                .andExpect(jsonPath("$.data.topRegions[0].recordCount").value(7));

        verify(travelStatisticsService).getStatistics(same(member));
    }

    @Test
    @DisplayName("토큰 없이 요청하면 401을 반환한다")
    void rejectsUnauthenticatedRequest() throws Exception {
        mockMvc.perform(get("/api/v1/travel-records/statistics"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("INVALID_ACCESS_TOKEN"));
    }

    private String bearer(Long id) {
        return "Bearer " + jwtProvider.issueAccessToken(id);
    }
}
