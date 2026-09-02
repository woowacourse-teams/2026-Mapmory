package com.mapmory.backend.travelrecord;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.mapmory.backend.auth.security.LoginMember;
import com.mapmory.backend.common.ProblemDetailFactory;
import com.mapmory.backend.common.handler.ValidationExceptionHandler;
import com.mapmory.backend.member.Member;
import com.mapmory.backend.recordmedia.ExpiringUrl;
import com.mapmory.backend.region.Region;
import com.mapmory.backend.region.RegionType;
import com.mapmory.backend.travelrecord.dto.CreateTravelRecordResponse;
import com.mapmory.backend.travelrecord.dto.TravelRecordDetailResponse;
import com.mapmory.backend.travelrecord.dto.TravelRecordRequest;
import com.mapmory.backend.travelrecord.dto.TravelRecordResponse;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentMatchers;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.MethodParameter;
import org.springframework.http.HttpStatus;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.support.WebDataBinderFactory;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.method.support.ModelAndViewContainer;

@ExtendWith(MockitoExtension.class)
class TravelRecordControllerTest {

    private static final long MEMBER_ID = 10L;
    private static final Member MEMBER = Member.of("테스터", UUID.randomUUID());

    static {
        ReflectionTestUtils.setField(MEMBER, "id", MEMBER_ID);
    }

    @Mock
    private TravelRecordService travelRecordService;

    @InjectMocks
    private TravelRecordController travelRecordController;

    // @LoginMember를 고정 Member로 해석하는 리졸버. standalone MockMvc에서 HTTP·JSON 레이어만
    // 검증하고, 실제 인증(401 등)은 SecurityIntegrationTest가 담당한다.
    private MockMvc mockMvcWithLoginMember() {
        return MockMvcBuilders.standaloneSetup(travelRecordController)
                .setControllerAdvice(new ValidationExceptionHandler(new ProblemDetailFactory()))
                .setCustomArgumentResolvers(new HandlerMethodArgumentResolver() {
                    @Override
                    public boolean supportsParameter(MethodParameter parameter) {
                        return parameter.hasParameterAnnotation(LoginMember.class);
                    }

                    @Override
                    public Object resolveArgument(
                            MethodParameter parameter,
                            ModelAndViewContainer mavContainer,
                            NativeWebRequest webRequest,
                            WebDataBinderFactory binderFactory
                    ) {
                        return MEMBER;
                    }
                })
                .build();
    }

    @Test
    void 여행_일지를_생성한다() {
        TravelRecordRequest request = new TravelRecordRequest(
                "JP",
                null,
                null,
                "일본 여행",
                "",
                LocalDate.of(2026, 8, 11),
                null,
                List.of()
        );
        TravelRecord travelRecord = TravelRecord.of(
                null,
                null,
                request.title(),
                request.content(),
                request.startDate(),
                request.endDate()
        );
        ReflectionTestUtils.setField(travelRecord, "id", 1L);

        when(travelRecordService.create(MEMBER, request.toCommand())).thenReturn(travelRecord);

        ResponseEntity<TravelRecordResponse<CreateTravelRecordResponse>> response =
                travelRecordController.create(MEMBER, request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(response.getBody()).isEqualTo(
                TravelRecordResponse.of(new CreateTravelRecordResponse(1L))
        );
        verify(travelRecordService).create(MEMBER, request.toCommand());
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "   "})
    void 제목이_비어_있으면_여행_일지를_생성하지_않는다(String title) throws Exception {
        String requestBody = validCreateRequestBody(title, "본문");

        mockMvcWithLoginMember().perform(post("/api/v1/travel-records")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.errors[0].field").value("title"));

        verify(travelRecordService, never()).create(eq(MEMBER), any(TravelRecordCommand.class));
    }

    @Test
    void 제목이_누락되면_여행_일지를_생성하지_않는다() throws Exception {
        mockMvcWithLoginMember().perform(post("/api/v1/travel-records")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "countryCode": "JP",
                                  "content": "본문",
                                  "startDate": "2026-08-11",
                                  "objectKeys": []
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.errors[0].field").value("title"));

        verify(travelRecordService, never()).create(eq(MEMBER), any(TravelRecordCommand.class));
    }

    @Test
    void 제목이_200자를_초과하면_여행_일지를_생성하지_않는다() throws Exception {
        String requestBody = validCreateRequestBody("가".repeat(201), "본문");

        mockMvcWithLoginMember().perform(post("/api/v1/travel-records")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.errors[0].field").value("title"));

        verify(travelRecordService, never()).create(eq(MEMBER), any(TravelRecordCommand.class));
    }

    @ParameterizedTest
    @ValueSource(strings = {"", " ", "kr", "KOR"})
    void 국가_코드_형식이_올바르지_않으면_여행_일지를_생성하지_않는다(String countryCode) throws Exception {
        mockMvcWithLoginMember().perform(post("/api/v1/travel-records")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createRequestBody(countryCode, null, null)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.errors[0].field").value("countryCode"));

        verify(travelRecordService, never()).create(eq(MEMBER), any(TravelRecordCommand.class));
    }

    @Test
    void 시도_코드가_공백이면_여행_일지를_생성하지_않는다() throws Exception {
        mockMvcWithLoginMember().perform(post("/api/v1/travel-records")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createRequestBody("KR", " ", "50110")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.errors[0].field").value("provinceCode"));

        verify(travelRecordService, never()).create(eq(MEMBER), any(TravelRecordCommand.class));
    }

    @Test
    void 시군구_코드가_공백이면_여행_일지를_생성하지_않는다() throws Exception {
        mockMvcWithLoginMember().perform(post("/api/v1/travel-records")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createRequestBody("KR", "49", " ")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.errors[0].field").value("districtCode"));

        verify(travelRecordService, never()).create(eq(MEMBER), any(TravelRecordCommand.class));
    }

    @Test
    void 본문이_null이면_여행_일지를_생성한다() throws Exception {
        TravelRecord travelRecord = TravelRecord.of(
                null,
                null,
                "일본 여행",
                null,
                LocalDate.of(2026, 8, 11),
                null
        );
        ReflectionTestUtils.setField(travelRecord, "id", 1L);
        when(travelRecordService.create(eq(MEMBER), any(TravelRecordCommand.class)))
                .thenReturn(travelRecord);

        mockMvcWithLoginMember().perform(post("/api/v1/travel-records")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "countryCode": "JP",
                                  "title": "일본 여행",
                                  "content": null,
                                  "startDate": "2026-08-11",
                                  "objectKeys": []
                                }
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.id").value(1L));

        verify(travelRecordService).create(eq(MEMBER), any(TravelRecordCommand.class));
    }

    @Test
    void 제목이_200자이고_본문이_빈_문자열이면_여행_일지를_생성한다() throws Exception {
        TravelRecord travelRecord = TravelRecord.of(
                null,
                null,
                "가".repeat(200),
                "",
                LocalDate.of(2026, 8, 11),
                null
        );
        ReflectionTestUtils.setField(travelRecord, "id", 1L);
        when(travelRecordService.create(eq(MEMBER), any(TravelRecordCommand.class)))
                .thenReturn(travelRecord);

        mockMvcWithLoginMember().perform(post("/api/v1/travel-records")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validCreateRequestBody("가".repeat(200), "")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.id").value(1L));

        verify(travelRecordService).create(eq(MEMBER), any(TravelRecordCommand.class));
    }

    @Test
    void 여행_일지_상세_조회를_서비스에_위임한다() {
        TravelRecordDetail detail = detail("제주 여행", "제주시를 걸었다.",
                List.of("mapmory/travel-records/a.jpg"));
        when(travelRecordService.findById(MEMBER, 101L)).thenReturn(detail);

        ResponseEntity<TravelRecordResponse<TravelRecordDetailResponse>> response =
                travelRecordController.findById(MEMBER, 101L);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isEqualTo(
                TravelRecordResponse.of(TravelRecordDetailResponse.from(detail))
        );
        verify(travelRecordService).findById(MEMBER, 101L);
    }

    @Test
    void 여행_일지_상세_HTTP_응답을_반환한다() throws Exception {
        TravelRecordDetail detail = detail("제주 여행", "제주시를 걸었다.",
                List.of("mapmory/travel-records/a.jpg"));
        when(travelRecordService.findById(MEMBER, 101L)).thenReturn(detail);

        mockMvcWithLoginMember().perform(get("/api/v1/travel-records/101"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value(101L))
                .andExpect(jsonPath("$.data.content").value("제주시를 걸었다."))
                .andExpect(jsonPath("$.data.region.country.code").value("KR"))
                .andExpect(jsonPath("$.data.region.district.code").value("50110"))
                .andExpect(jsonPath("$.data.objectKeys[0]")
                        .value("mapmory/travel-records/a.jpg"))
                .andExpect(jsonPath("$.data.media[0].objectKey")
                        .value("mapmory/travel-records/a.jpg"))
                .andExpect(jsonPath("$.data.media[0].viewUrl")
                        .value("https://download.example/mapmory/travel-records/a.jpg"))
                .andExpect(jsonPath("$.data.media[0].viewUrlExpiresIn").value(300L));
    }

    @Test
    void 여행_일지_목록에_썸네일_URL을_반환한다() throws Exception {
        TravelRecord travelRecord = travelRecord("제주 여행", "");
        TravelRecordSummaries summaries = new TravelRecordSummaries(
                new PageImpl<>(List.of(travelRecord), PageRequest.of(0, 20), 1),
                Map.of(),
                Map.of(101L, new ExpiringUrl(
                        "https://download.example/mapmory/travel-records/a.jpg",
                        300L
                ))
        );
        when(travelRecordService.findAll(MEMBER, null, null, null, null, 0, 20))
                .thenReturn(summaries);

        mockMvcWithLoginMember().perform(get("/api/v1/travel-records"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items[0].thumbnailUrl")
                        .value("https://download.example/mapmory/travel-records/a.jpg"))
                .andExpect(jsonPath("$.data.items[0].thumbnailUrlExpiresIn").value(300L));
    }

    @Test
    void 여행_일지를_수정한다() throws Exception {
        TravelRecordDetail detail = detail("수정된 제주 여행", "수정된 본문",
                List.of("travel-records/10/b.jpg"));
        when(travelRecordService.update(
                ArgumentMatchers.eq(MEMBER),
                ArgumentMatchers.eq(101L),
                ArgumentMatchers.any(TravelRecordCommand.class)
        )).thenReturn(detail);

        mockMvcWithLoginMember().perform(put("/api/v1/travel-records/101")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "countryCode": "KR",
                                  "provinceCode": "49",
                                  "districtCode": "50110",
                                  "title": "수정된 제주 여행",
                                  "content": "수정된 본문",
                                  "startDate": "2026-08-11",
                                  "endDate": "2026-08-13",
                                  "objectKeys": ["travel-records/10/b.jpg"]
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value(101L))
                .andExpect(jsonPath("$.data.title").value("수정된 제주 여행"))
                .andExpect(jsonPath("$.data.region.district.code").value("50110"))
                .andExpect(jsonPath("$.data.objectKeys[0]")
                        .value("travel-records/10/b.jpg"))
                .andExpect(jsonPath("$.data.media[0].viewUrl")
                        .value("https://download.example/travel-records/10/b.jpg"));
    }

    @Test
    void 여행_일지를_삭제한다() throws Exception {
        mockMvcWithLoginMember().perform(delete("/api/v1/travel-records/101"))
                .andExpect(status().isNoContent());

        verify(travelRecordService).delete(MEMBER, 101L);
    }

    private TravelRecordDetail detail(String title, String content, List<String> objectKeys) {
        TravelRecord travelRecord = travelRecord(title, content);
        travelRecord.synchronizeMedia(objectKeys);
        List<MediaView> mediaViews = travelRecord.getMedia().stream()
                .map(recordMedia -> new MediaView(recordMedia, new ExpiringUrl(
                        "https://download.example/" + recordMedia.getObjectKey(),
                        300L
                )))
                .toList();

        return new TravelRecordDetail(travelRecord, List.of(), mediaViews);
    }

    private TravelRecord travelRecord(String title, String content) {
        Region country = Region.of(null, null, "KR", "대한민국", RegionType.COUNTRY);
        Region province = Region.of(country, country, "49", "제주특별자치도", RegionType.PROVINCE);
        Region district = Region.of(province, country, "50110", "제주시", RegionType.DISTRICT);
        TravelRecord travelRecord = TravelRecord.of(
                null,
                district,
                title,
                content,
                LocalDate.of(2026, 8, 11),
                LocalDate.of(2026, 8, 13)
        );
        ReflectionTestUtils.setField(travelRecord, "id", 101L);

        return travelRecord;
    }

    private String validCreateRequestBody(String title, String content) {
        return """
                {
                  "countryCode": "JP",
                  "title": "%s",
                  "content": "%s",
                  "startDate": "2026-08-11",
                  "objectKeys": []
                }
                """.formatted(title, content);
    }

    private String createRequestBody(String countryCode, String provinceCode, String districtCode) {
        return """
                {
                  "countryCode": "%s",
                  "provinceCode": %s,
                  "districtCode": %s,
                  "title": "여행",
                  "content": "",
                  "startDate": "2026-08-11",
                  "objectKeys": []
                }
                """.formatted(
                countryCode,
                nullableJsonString(provinceCode),
                nullableJsonString(districtCode)
        );
    }

    private String nullableJsonString(String value) {
        return value == null ? "null" : "\"%s\"".formatted(value);
    }
}
