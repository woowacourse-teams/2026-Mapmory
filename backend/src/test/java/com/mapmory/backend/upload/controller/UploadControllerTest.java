package com.mapmory.backend.upload.controller;

import static org.mockito.Mockito.mock;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.mapmory.backend.auth.security.LoginMember;
import com.mapmory.backend.common.ProblemDetailFactory;
import com.mapmory.backend.common.handler.BusinessExceptionHandler;
import com.mapmory.backend.common.handler.ValidationExceptionHandler;
import com.mapmory.backend.member.Member;
import com.mapmory.backend.member.application.ActiveMemberGuard;
import com.mapmory.backend.upload.domain.UploadPolicy;
import com.mapmory.backend.upload.service.UploadService;
import com.mapmory.backend.upload.service.port.PresignedRequest;
import com.mapmory.backend.upload.service.port.PresignedUrlProvider;
import com.mapmory.backend.upload.storage.S3ObjectKeyGenerator;
import com.mapmory.backend.upload.storage.S3StorageProperties;
import java.net.URI;
import java.time.Duration;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.core.MethodParameter;
import org.springframework.http.MediaType;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.util.unit.DataSize;
import org.springframework.web.bind.support.WebDataBinderFactory;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.method.support.ModelAndViewContainer;

class UploadControllerTest {

    private static final long MEMBER_ID = 10L;
    private static final long TEN_MEGABYTES = DataSize.ofMegabytes(10).toBytes();
    private static final Duration EXPIRATION = Duration.ofMinutes(5);
    private static final Member MEMBER = Member.of("테스터", UUID.randomUUID());

    static {
        ReflectionTestUtils.setField(MEMBER, "id", MEMBER_ID);
    }

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        S3StorageProperties storageProperties = new S3StorageProperties(
                "mapmory-test", "ap-northeast-2", "mapmory", EXPIRATION, null);
        UploadService uploadService = new UploadService(
                new UploadPolicy(TEN_MEGABYTES, 100),
                new S3ObjectKeyGenerator(storageProperties),
                new FakePresignedUrlProvider(),
                mock(ActiveMemberGuard.class)
        );
        ProblemDetailFactory problemDetailFactory = new ProblemDetailFactory();

        mockMvc = MockMvcBuilders.standaloneSetup(new UploadController(uploadService))
                .setCustomArgumentResolvers(loginMemberResolver())
                .setControllerAdvice(
                        new BusinessExceptionHandler(problemDetailFactory),
                        new ValidationExceptionHandler(problemDetailFactory)
                )
                .build();
    }

    @Test
    void Presigned_URL을_발급한다() throws Exception {
        mockMvc.perform(post("/api/v1/uploads/presigned-urls")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(fileRequest("image/jpeg", 3_145_728L)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.uploads[0].objectKey")
                        .value(org.hamcrest.Matchers.matchesPattern(
                                "mapmory/travel-records/10/.+\\.jpg")))
                .andExpect(jsonPath("$.data.uploads[0].presignedUrl")
                        .value(org.hamcrest.Matchers.startsWith("https://upload.example/")))
                .andExpect(jsonPath("$.data.uploads[0].method").value("PUT"))
                .andExpect(jsonPath("$.data.uploads[0].contentType").value("image/jpeg"))
                .andExpect(jsonPath("$.data.uploads[0].expiresIn").value(300));
    }

    @Test
    void heif_파일은_heic_키로_발급한다() throws Exception {
        mockMvc.perform(post("/api/v1/uploads/presigned-urls")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(fileRequest("IMG_0001.heif", "image/heic", 1024L)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.uploads[0].objectKey")
                        .value(org.hamcrest.Matchers.matchesPattern(
                                "mapmory/travel-records/10/.+\\.heic")))
                .andExpect(jsonPath("$.data.uploads[0].contentType").value("image/heic"));
    }

    @Test
    void 허용되지_않은_파일_형식은_400_ProblemDetails로_응답한다() throws Exception {
        expectUploadError(fileRequest("image/gif", 1024L), "INVALID_FILE_TYPE");
    }

    @Test
    void 크기를_초과한_파일은_400_ProblemDetails로_응답한다() throws Exception {
        expectUploadError(fileRequest("image/jpeg", DataSize.ofMegabytes(10).toBytes() + 1),
                "FILE_SIZE_EXCEEDED");
    }

    @Test
    void 개수를_초과한_파일은_400_ProblemDetails로_응답한다() throws Exception {
        String files = IntStream.range(0, 101)
                .mapToObj(index -> """
                        {"fileName":"image.jpg","contentType":"image/jpeg","fileSize":1024}
                        """.trim())
                .collect(Collectors.joining(","));

        expectUploadError("{\"files\":[" + files + "]}", "TOO_MANY_FILES");
    }

    private void expectUploadError(String request, String errorCode) throws Exception {
        mockMvc.perform(post("/api/v1/uploads/presigned-urls")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(request))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.instance").value("/api/v1/uploads/presigned-urls"))
                .andExpect(jsonPath("$.code").value(errorCode));
    }

    private static String fileRequest(String contentType, long fileSize) {
        return fileRequest("image.jpg", contentType, fileSize);
    }

    private static String fileRequest(String fileName, String contentType, long fileSize) {
        return """
                {
                  "files": [
                    {
                      "fileName": "%s",
                      "contentType": "%s",
                      "fileSize": %d
                    }
                  ]
                }
                """.formatted(fileName, contentType, fileSize);
    }

    private static HandlerMethodArgumentResolver loginMemberResolver() {
        return new HandlerMethodArgumentResolver() {
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
        };
    }

    private static class FakePresignedUrlProvider implements PresignedUrlProvider {

        @Override
        public PresignedRequest createPresignedPutUrl(
                String objectKey,
                String contentType,
                long contentLength
        ) {
            return new PresignedRequest(URI.create("https://upload.example/" + objectKey), "PUT", EXPIRATION);
        }

        @Override
        public PresignedRequest createPresignedGetUrl(String objectKey) {
            return new PresignedRequest(URI.create("https://download.example/" + objectKey), "GET", EXPIRATION);
        }
    }
}
