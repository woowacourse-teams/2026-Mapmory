package com.mapmory.backend.upload.controller;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.mapmory.backend.auth.security.LoginMember;
import com.mapmory.backend.common.ProblemDetailFactory;
import com.mapmory.backend.common.handler.BusinessExceptionHandler;
import com.mapmory.backend.common.handler.ValidationExceptionHandler;
import com.mapmory.backend.member.Member;
import com.mapmory.backend.upload.policy.ObjectKeyGenerator;
import com.mapmory.backend.upload.storage.S3StorageProperties;
import com.mapmory.backend.upload.policy.UploadPolicy;
import com.mapmory.backend.upload.policy.UploadPolicyProperties;
import com.mapmory.backend.upload.service.UploadService;
import com.mapmory.backend.upload.storage.PresignedUrlProvider;
import java.net.URI;
import java.time.Duration;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.core.MethodParameter;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.util.unit.DataSize;
import org.springframework.web.bind.support.WebDataBinderFactory;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.method.support.ModelAndViewContainer;

class UploadControllerTest {

    private static final long MEMBER_ID = 10L;
    private static final Member MEMBER = Member.of("테스터", UUID.randomUUID());

    static {
        ReflectionTestUtils.setField(MEMBER, "id", MEMBER_ID);
    }

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        UploadPolicyProperties properties = new UploadPolicyProperties(
                DataSize.ofMegabytes(10),
                100,
                Duration.ofMinutes(5)
        );
        UploadService uploadService = new UploadService(
                new UploadPolicy(properties),
                new ObjectKeyGenerator(new S3StorageProperties("mapmory-test", "ap-northeast-2", "", null)),
                new FakePresignedUrlProvider(),
                properties
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
                                "travel-records/10/.+\\.jpg")))
                .andExpect(jsonPath("$.data.uploads[0].presignedUrl")
                        .value(org.hamcrest.Matchers.startsWith("https://upload.example/")))
                .andExpect(jsonPath("$.data.uploads[0].method").value("PUT"))
                .andExpect(jsonPath("$.data.uploads[0].contentType").value("image/jpeg"))
                .andExpect(jsonPath("$.data.uploads[0].expiresIn").value(300));
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
        return """
                {
                  "files": [
                    {
                      "fileName": "image.jpg",
                      "contentType": "%s",
                      "fileSize": %d
                    }
                  ]
                }
                """.formatted(contentType, fileSize);
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
        public URI createPresignedPutUrl(
                String objectKey,
                String contentType,
                long contentLength,
                Duration expiration
        ) {
            return URI.create("https://upload.example/" + objectKey);
        }

        @Override
        public URI createPresignedGetUrl(String objectKey, Duration expiration) {
            return URI.create("https://download.example/" + objectKey);
        }
    }
}
