package com.mapmory.backend.auth.infrastructure.kakao;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import com.mapmory.backend.auth.exception.AuthErrorCode;
import com.mapmory.backend.auth.infrastructure.kakao.config.KakaoProperties;
import com.mapmory.backend.common.exception.BusinessException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

class KakaoApiClientTest {

    private static final String TOKEN_INFO_URI = "https://kapi.kakao.com/v1/user/access_token_info";
    private static final String USER_INFO_URI = "https://kapi.kakao.com/v2/user/me";
    private static final long APP_ID = 1_234_567L;

    private MockRestServiceServer server;
    private KakaoApiClient kakaoApiClient;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder();
        server = MockRestServiceServer.bindTo(builder).build();
        kakaoApiClient = new KakaoApiClient(
                builder.build(), new KakaoProperties(TOKEN_INFO_URI, USER_INFO_URI, APP_ID));
    }

    @Test
    void 정상_응답이면_회원정보를_반환한다() {
        expectIssuedForUs();
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
        expectIssuedForUs();
        server.expect(requestTo(USER_INFO_URI))
                .andRespond(withStatus(HttpStatus.UNAUTHORIZED));

        assertThatThrownBy(() -> kakaoApiClient.fetchUser("bad-token"))
                .isInstanceOfSatisfying(BusinessException.class, exception ->
                        assertThat(exception.getErrorCode()).isEqualTo(AuthErrorCode.INVALID_KAKAO_TOKEN));
    }

    @Test
    void 카카오_5xx는_KAKAO_UNAVAILABLE로_변환한다() {
        expectIssuedForUs();
        server.expect(requestTo(USER_INFO_URI))
                .andRespond(withStatus(HttpStatus.INTERNAL_SERVER_ERROR));

        assertThatThrownBy(() -> kakaoApiClient.fetchUser("token"))
                .isInstanceOfSatisfying(BusinessException.class, exception -> {
                    assertThat(exception.getErrorCode()).isEqualTo(AuthErrorCode.KAKAO_UNAVAILABLE);
                    assertThat(exception.getCause()).isInstanceOf(RestClientResponseException.class);
                });
    }

    @Test
    void 회원번호가_없는_응답은_KAKAO_UNAVAILABLE로_변환한다() {
        expectIssuedForUs();
        server.expect(requestTo(USER_INFO_URI))
                .andRespond(withSuccess("{\"kakao_account\":{}}", MediaType.APPLICATION_JSON));

        assertThatThrownBy(() -> kakaoApiClient.fetchUser("token"))
                .isInstanceOfSatisfying(BusinessException.class, exception ->
                        assertThat(exception.getErrorCode()).isEqualTo(AuthErrorCode.KAKAO_UNAVAILABLE));
    }

    @Test
    void 빈_응답은_KAKAO_UNAVAILABLE로_변환한다() {
        expectIssuedForUs();
        server.expect(requestTo(USER_INFO_URI))
                .andRespond(withSuccess("", MediaType.APPLICATION_JSON));

        assertThatThrownBy(() -> kakaoApiClient.fetchUser("token"))
                .isInstanceOfSatisfying(BusinessException.class, exception ->
                        assertThat(exception.getErrorCode()).isEqualTo(AuthErrorCode.KAKAO_UNAVAILABLE));
    }

    @Test
    void 다른_앱에서_발급된_토큰은_INVALID_KAKAO_TOKEN으로_거부하고_회원정보를_조회하지_않는다() {
        server.expect(requestTo(TOKEN_INFO_URI))
                .andRespond(withSuccess(tokenInfo(9_999_999L), MediaType.APPLICATION_JSON));

        assertThatThrownBy(() -> kakaoApiClient.fetchUser("other-app-token"))
                .isInstanceOfSatisfying(BusinessException.class, exception ->
                        assertThat(exception.getErrorCode()).isEqualTo(AuthErrorCode.INVALID_KAKAO_TOKEN));
        server.verify();
    }

    @Test
    void 앱_ID가_설정되지_않으면_모든_토큰을_거부한다() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer unconfiguredServer = MockRestServiceServer.bindTo(builder).build();
        KakaoApiClient unconfigured = new KakaoApiClient(
                builder.build(), new KakaoProperties(TOKEN_INFO_URI, USER_INFO_URI, null));
        unconfiguredServer.expect(requestTo(TOKEN_INFO_URI))
                .andRespond(withSuccess(tokenInfo(APP_ID), MediaType.APPLICATION_JSON));

        assertThatThrownBy(() -> unconfigured.fetchUser("token"))
                .isInstanceOfSatisfying(BusinessException.class, exception ->
                        assertThat(exception.getErrorCode()).isEqualTo(AuthErrorCode.INVALID_KAKAO_TOKEN));
    }

    @Test
    void 토큰_정보_조회의_4xx는_INVALID_KAKAO_TOKEN으로_변환한다() {
        server.expect(requestTo(TOKEN_INFO_URI))
                .andRespond(withStatus(HttpStatus.UNAUTHORIZED));

        assertThatThrownBy(() -> kakaoApiClient.fetchUser("bad-token"))
                .isInstanceOfSatisfying(BusinessException.class, exception ->
                        assertThat(exception.getErrorCode()).isEqualTo(AuthErrorCode.INVALID_KAKAO_TOKEN));
    }

    @Test
    void 토큰_정보_조회의_5xx는_KAKAO_UNAVAILABLE로_변환한다() {
        server.expect(requestTo(TOKEN_INFO_URI))
                .andRespond(withStatus(HttpStatus.SERVICE_UNAVAILABLE));

        assertThatThrownBy(() -> kakaoApiClient.fetchUser("token"))
                .isInstanceOfSatisfying(BusinessException.class, exception ->
                        assertThat(exception.getErrorCode()).isEqualTo(AuthErrorCode.KAKAO_UNAVAILABLE));
    }

    @Test
    void 앱_ID가_없는_토큰_정보는_KAKAO_UNAVAILABLE로_변환한다() {
        server.expect(requestTo(TOKEN_INFO_URI))
                .andRespond(withSuccess("{\"id\":12345}", MediaType.APPLICATION_JSON));

        assertThatThrownBy(() -> kakaoApiClient.fetchUser("token"))
                .isInstanceOfSatisfying(BusinessException.class, exception ->
                        assertThat(exception.getErrorCode()).isEqualTo(AuthErrorCode.KAKAO_UNAVAILABLE));
    }

    private void expectIssuedForUs() {
        server.expect(requestTo(TOKEN_INFO_URI))
                .andRespond(withSuccess(tokenInfo(APP_ID), MediaType.APPLICATION_JSON));
    }

    private static String tokenInfo(long appId) {
        return "{\"id\":12345,\"expires_in\":7199,\"app_id\":" + appId + "}";
    }
}
