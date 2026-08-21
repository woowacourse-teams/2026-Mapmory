package com.mapmory.backend.travelrecord.mapsummary.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.ArgumentMatchers.same;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.mapmory.backend.auth.jwt.JwtConfig;
import com.mapmory.backend.auth.jwt.JwtProvider;
import com.mapmory.backend.common.ProblemDetailFactory;
import com.mapmory.backend.common.exception.BusinessException;
import com.mapmory.backend.member.Member;
import com.mapmory.backend.member.MemberRepository;
import com.mapmory.backend.region.RegionType;
import com.mapmory.backend.region.exception.RegionErrorCode;
import com.mapmory.backend.travelrecord.mapsummary.dto.RegionMapSummaryResponse;
import com.mapmory.backend.travelrecord.mapsummary.policy.MapColorLevel;
import com.mapmory.backend.travelrecord.mapsummary.service.RegionMapSummaryService;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(RegionMapSummaryController.class)
@Import({JwtConfig.class, JwtProvider.class, ProblemDetailFactory.class})
@DisplayName("Region 지도 요약 API")
class RegionMapSummaryControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JwtProvider jwtProvider;

    @MockitoBean
    private MemberRepository memberRepository;

    @MockitoBean
    private RegionMapSummaryService regionMapSummaryService;

    private Long memberId;
    private Member member;

    @BeforeEach
    void setUpMember() {
        memberId = 10L;
        member = Member.of("지도 요약 테스트 회원", UUID.randomUUID());
        when(memberRepository.findById(memberId)).thenReturn(Optional.of(member));
    }

    private String bearer(Long memberId) {
        return "Bearer " + jwtProvider.issueAccessToken(memberId);
    }

    @Nested
    @DisplayName("GET /api/v1/travel-records/map-summary/regions/roots")
    class GetRootSummaries {

        @Test
        @DisplayName("회원의 루트 지역별 지도 요약을 의미 기반 단계와 함께 반환한다")
        void returnsRootSummaries() throws Exception {
            when(regionMapSummaryService.getSummaries(any(Member.class), isNull(), isNull())).thenReturn(List.of(
                    new RegionMapSummaryResponse(
                            1L,
                            "KR",
                            RegionType.COUNTRY,
                            "대한민국",
                            3L,
                            MapColorLevel.MEDIUM
                    )
            ));

            mockMvc.perform(get("/api/v1/travel-records/map-summary/regions/roots")
                            .header(HttpHeaders.AUTHORIZATION, bearer(memberId)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data[0].regionId").value(1))
                    .andExpect(jsonPath("$.data[0].code").value("KR"))
                    .andExpect(jsonPath("$.data[0].regionType").value("COUNTRY"))
                    .andExpect(jsonPath("$.data[0].name").value("대한민국"))
                    .andExpect(jsonPath("$.data[0].count").value(3))
                    .andExpect(jsonPath("$.data[0].level").value("MEDIUM"));

            verify(regionMapSummaryService).getSummaries(
                    same(member),
                    isNull(),
                    isNull()
            );
        }

        @Test
        @DisplayName("tagId를 지도 요약 서비스에 전달한다")
        void passesTagId() throws Exception {
            when(regionMapSummaryService.getSummaries(any(Member.class), isNull(), eq(7L)))
                    .thenReturn(List.of());

            mockMvc.perform(get("/api/v1/travel-records/map-summary/regions/roots")
                            .queryParam("tagId", "7")
                            .header(HttpHeaders.AUTHORIZATION, bearer(memberId)))
                    .andExpect(status().isOk());

            verify(regionMapSummaryService).getSummaries(
                    same(member),
                    isNull(),
                    eq(7L)
            );
        }

        @Test
        @DisplayName("토큰 없이 요청하면 401을 반환한다")
        void rejectsUnauthenticatedRequest() throws Exception {
            mockMvc.perform(get("/api/v1/travel-records/map-summary/regions/roots"))
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.code").value("INVALID_ACCESS_TOKEN"));
        }

        @Test
        @DisplayName("회원을 찾을 수 없으면 MEMBER_NOT_FOUND를 반환한다")
        void returnsMemberNotFound() throws Exception {
            mockMvc.perform(get("/api/v1/travel-records/map-summary/regions/roots")
                            .header(HttpHeaders.AUTHORIZATION, bearer(Long.MAX_VALUE)))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.code").value("MEMBER_NOT_FOUND"));
        }
    }

    @Nested
    @DisplayName("GET /api/v1/travel-records/map-summary/regions/{regionId}/children")
    class GetChildSummaries {

        @Test
        @DisplayName("선택 지역의 직속 하위 지역별 지도 요약을 반환한다")
        void returnsChildSummaries() throws Exception {
            when(regionMapSummaryService.getSummaries(any(Member.class), eq(1L), isNull())).thenReturn(List.of(
                    new RegionMapSummaryResponse(
                            15L,
                            "49",
                            RegionType.PROVINCE,
                            "제주특별자치도",
                            3L,
                            MapColorLevel.MEDIUM
                    )
            ));

            mockMvc.perform(get("/api/v1/travel-records/map-summary/regions/1/children")
                            .header(HttpHeaders.AUTHORIZATION, bearer(memberId)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data[0].regionId").value(15))
                    .andExpect(jsonPath("$.data[0].code").value("49"))
                    .andExpect(jsonPath("$.data[0].regionType").value("PROVINCE"))
                    .andExpect(jsonPath("$.data[0].count").value(3))
                    .andExpect(jsonPath("$.data[0].level").value("MEDIUM"));

            verify(regionMapSummaryService).getSummaries(
                    same(member),
                    eq(1L),
                    isNull()
            );
        }

        @Test
        @DisplayName("지역 ID가 양수가 아니면 400을 반환한다")
        void rejectsNonPositiveRegionId() throws Exception {
            mockMvc.perform(get("/api/v1/travel-records/map-summary/regions/0/children")
                            .header(HttpHeaders.AUTHORIZATION, bearer(memberId)))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
        }

        @Test
        @DisplayName("상위 지역을 찾을 수 없으면 REGION_NOT_FOUND를 반환한다")
        void returnsRegionNotFound() throws Exception {
            when(regionMapSummaryService.getSummaries(any(Member.class), eq(999L), isNull()))
                    .thenThrow(new BusinessException(RegionErrorCode.REGION_NOT_FOUND));

            mockMvc.perform(get("/api/v1/travel-records/map-summary/regions/999/children")
                            .header(HttpHeaders.AUTHORIZATION, bearer(memberId)))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.code").value("REGION_NOT_FOUND"));
        }
    }
}
