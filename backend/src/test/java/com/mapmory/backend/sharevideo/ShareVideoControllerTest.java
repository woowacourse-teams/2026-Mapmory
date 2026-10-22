package com.mapmory.backend.sharevideo;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.mapmory.backend.auth.token.jwt.JwtConfig;
import com.mapmory.backend.auth.token.jwt.JwtProvider;
import com.mapmory.backend.common.ProblemDetailFactory;
import com.mapmory.backend.common.exception.BusinessException;
import com.mapmory.backend.member.Member;
import jakarta.persistence.EntityManager;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(ShareVideoController.class)
@Import({JwtConfig.class, JwtProvider.class, ProblemDetailFactory.class})
class ShareVideoControllerTest {

    private static final long MEMBER_ID = 10L;

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JwtProvider jwtProvider;

    @MockitoBean
    private EntityManager entityManager;

    @MockitoBean
    private ShareVideoService shareVideoService;

    private Member member;

    @BeforeEach
    void setUp() {
        member = Member.of("공유 영상 테스트 회원", UUID.randomUUID());
        when(entityManager.getReference(Member.class, MEMBER_ID)).thenReturn(member);
    }

    @Test
    void 영상_작업_상태를_조회한다() throws Exception {
        ShareVideoJob job = pendingJob(15L);
        when(shareVideoService.getJob(any(Member.class), eq(15L))).thenReturn(job);

        mockMvc.perform(get("/api/v1/share-videos/15")
                        .header(HttpHeaders.AUTHORIZATION, bearerToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.jobId").value(15))
                .andExpect(jsonPath("$.data.status").value("PENDING"));

        verify(shareVideoService).getJob(member, 15L);
    }

    @Test
    void 완료된_영상의_다운로드_URL을_조회한다() throws Exception {
        ShareVideoDownloadUrl downloadUrl = new ShareVideoDownloadUrl(
                "https://download.example/video.mp4",
                300L
        );
        when(shareVideoService.createDownloadUrl(any(Member.class), eq(15L)))
                .thenReturn(downloadUrl);

        mockMvc.perform(get("/api/v1/share-videos/15/download-url")
                        .header(HttpHeaders.AUTHORIZATION, bearerToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.jobId").value(15))
                .andExpect(jsonPath("$.data.downloadUrl")
                        .value("https://download.example/video.mp4"))
                .andExpect(jsonPath("$.data.downloadUrlExpiresIn").value(300));

        verify(shareVideoService).createDownloadUrl(member, 15L);
    }

    @Test
    void 준비되지_않은_영상은_409를_반환한다() throws Exception {
        when(shareVideoService.createDownloadUrl(any(Member.class), eq(15L)))
                .thenThrow(new BusinessException(ShareVideoErrorCode.SHARE_VIDEO_NOT_READY));

        mockMvc.perform(get("/api/v1/share-videos/15/download-url")
                        .header(HttpHeaders.AUTHORIZATION, bearerToken()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("SHARE_VIDEO_NOT_READY"));
    }

    @Test
    void 존재하지_않는_영상_작업은_404를_반환한다() throws Exception {
        when(shareVideoService.getJob(any(Member.class), eq(999L)))
                .thenThrow(new BusinessException(ShareVideoErrorCode.SHARE_VIDEO_JOB_NOT_FOUND));

        mockMvc.perform(get("/api/v1/share-videos/999")
                        .header(HttpHeaders.AUTHORIZATION, bearerToken()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("SHARE_VIDEO_JOB_NOT_FOUND"));
    }

    @Test
    void 작업_ID가_양수가_아니면_400을_반환한다() throws Exception {
        mockMvc.perform(get("/api/v1/share-videos/0")
                        .header(HttpHeaders.AUTHORIZATION, bearerToken()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
    }

    private ShareVideoJob pendingJob(long jobId) {
        ShareVideoJob job = ShareVideoJob.pending(
                member,
                "TRAVEL_TIMELINE",
                """
                {
                  "version": 1,
                  "stops": []
                }
                """
        );
        ReflectionTestUtils.setField(job, "id", jobId);
        return job;
    }

    private String bearerToken() {
        return "Bearer " + jwtProvider.issueAccessToken(MEMBER_ID);
    }
}
