package com.mapmory.backend.auth.kakao;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import com.mapmory.backend.auth.exception.AuthErrorCode;
import com.mapmory.backend.common.exception.BusinessException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

class KakaoApiClientTest {

    private static final String USER_INFO_URI = "https://kapi.kakao.com/v2/user/me";

    private MockRestServiceServer server;
    private KakaoApiClient kakaoApiClient;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder();
        server = MockRestServiceServer.bindTo(builder).build();
        kakaoApiClient = new KakaoApiClient(builder.build(), new KakaoProperties(USER_INFO_URI));
    }

    @Test
    void 정상_응답이면_회원정보를_반환한다() {
        server.expect(requestTo(USER_INFO_URI))
                .andRespond(withSuccess(
                        "{\"id\":12345,\"kakao_account\":{\"profile\":{\"nickname\":\"소현\"}}}",
                        MediaType.APPLICATION_JSON));

        KakaoUserResponse response = kakaoApiClient.fetchUser("token");

        assertThat(response.id()).isEqualTo(12345L);
        assertThat(response.nickname()).isEqualTo("소현");
    }

    @Test
    void 카카오_4xx는_INVALID_KAKAO_TOKEN으로_변환한다() {
        server.expect(requestTo(USER_INFO_URI))
                .andRespond(withStatus(HttpStatus.UNAUTHORIZED));

        assertThatThrownBy(() -> kakaoApiClient.fetchUser("bad-token"))
                .isInstanceOfSatisfying(BusinessException.class, exception ->
                        assertThat(exception.getErrorCode()).isEqualTo(AuthErrorCode.INVALID_KAKAO_TOKEN));
    }

    @Test
    void 카카오_5xx는_KAKAO_UNAVAILABLE로_변환한다() {
        server.expect(requestTo(USER_INFO_URI))
                .andRespond(withStatus(HttpStatus.INTERNAL_SERVER_ERROR));

        assertThatThrownBy(() -> kakaoApiClient.fetchUser("token"))
                .isInstanceOfSatisfying(BusinessException.class, exception ->
                        assertThat(exception.getErrorCode()).isEqualTo(AuthErrorCode.KAKAO_UNAVAILABLE));
    }
}
