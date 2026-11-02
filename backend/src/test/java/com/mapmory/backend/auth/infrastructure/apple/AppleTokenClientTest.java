package com.mapmory.backend.auth.infrastructure.apple;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withBadRequest;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import com.mapmory.backend.auth.application.model.AppleReauthentication;
import com.mapmory.backend.auth.exception.AuthErrorCode;
import com.mapmory.backend.auth.infrastructure.apple.config.AppleProperties;
import com.mapmory.backend.common.exception.BusinessException;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;

class AppleTokenClientTest {

    private static final String TOKEN_URI = "https://appleid.apple.com/auth/token";
    private static final String REVOKE_URI = "https://appleid.apple.com/auth/revoke";
    private static final String BUNDLE_ID = "com.mapmory.ios";

    private MockRestServiceServer server;
    private AppleTokenClient client;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder();
        server = MockRestServiceServer.bindTo(builder).build();
        AppleClientSecretFactory secretFactory = mock(AppleClientSecretFactory.class);
        given(secretFactory.create(anyString())).willReturn("client-secret");
        AppleProperties properties = new AppleProperties(List.of(BUNDLE_ID), "jwks", "team", "key", "pem", TOKEN_URI, REVOKE_URI);
        AppleIdentityTokenVerifier verifier = mock(AppleIdentityTokenVerifier.class);
        given(verifier.verify("apple-id")).willReturn(new AppleUser("apple-sub", BUNDLE_ID));
        given(verifier.verify("other-id")).willReturn(new AppleUser("other-sub", BUNDLE_ID));
        client = new AppleTokenClient(builder.build(), secretFactory, verifier, properties);
    }

    @Test
    void code를_토큰으로_바꾼_뒤_refresh_token을_철회한다() {
        server.expect(requestTo(TOKEN_URI))
                .andExpect(method(HttpMethod.POST))
                .andExpect(content().formData(form(
                        "client_id", BUNDLE_ID, "client_secret", "client-secret",
                        "code", "auth-code", "grant_type", "authorization_code")))
                .andRespond(withSuccess(
                        "{\"access_token\":\"apple-access\",\"refresh_token\":\"apple-refresh\",\"id_token\":\"apple-id\"}",
                        MediaType.APPLICATION_JSON));
        server.expect(requestTo(REVOKE_URI))
                .andExpect(method(HttpMethod.POST))
                .andExpect(content().formData(form(
                        "client_id", BUNDLE_ID, "client_secret", "client-secret",
                        "token", "apple-refresh", "token_type_hint", "refresh_token")))
                .andRespond(withSuccess());

        client.revoke(new AppleReauthentication("apple-sub", BUNDLE_ID), "auth-code");

        server.verify();
    }

    @Test
    void refresh_token이_없으면_access_token을_철회한다() {
        server.expect(requestTo(TOKEN_URI))
                .andRespond(withSuccess("{\"access_token\":\"apple-access\",\"id_token\":\"apple-id\"}", MediaType.APPLICATION_JSON));
        server.expect(requestTo(REVOKE_URI))
                .andExpect(content().formData(form(
                        "client_id", BUNDLE_ID, "client_secret", "client-secret",
                        "token", "apple-access", "token_type_hint", "access_token")))
                .andRespond(withSuccess());

        client.revoke(new AppleReauthentication("apple-sub", BUNDLE_ID), "auth-code");

        server.verify();
    }

    @Test
    void code가_만료됐거나_이미_쓰였으면_재인증_실패이고_철회하지_않는다() {
        server.expect(requestTo(TOKEN_URI))
                .andRespond(withBadRequest().body("{\"error\":\"invalid_grant\"}").contentType(MediaType.APPLICATION_JSON));

        assertFailsWith(AuthErrorCode.APPLE_REAUTHENTICATION_FAILED);
        server.verify();
    }

    @Test
    void 설정_오류로_거절되면_APPLE_UNAVAILABLE이다() {
        server.expect(requestTo(TOKEN_URI))
                .andRespond(withBadRequest().body("{\"error\":\"invalid_client\"}").contentType(MediaType.APPLICATION_JSON));

        assertFailsWith(AuthErrorCode.APPLE_UNAVAILABLE);
    }

    @Test
    void 교환에서_애플_장애면_APPLE_UNAVAILABLE이다() {
        server.expect(requestTo(TOKEN_URI)).andRespond(withServerError());

        assertFailsWith(AuthErrorCode.APPLE_UNAVAILABLE);
    }

    @Test
    void 토큰이_없는_교환_응답은_APPLE_UNAVAILABLE이다() {
        server.expect(requestTo(TOKEN_URI)).andRespond(withSuccess("{}", MediaType.APPLICATION_JSON));

        assertFailsWith(AuthErrorCode.APPLE_UNAVAILABLE);
    }

    @Test
    void 철회에서_실패하면_APPLE_UNAVAILABLE이다() {
        server.expect(requestTo(TOKEN_URI))
                .andRespond(withSuccess("{\"refresh_token\":\"apple-refresh\",\"id_token\":\"apple-id\"}", MediaType.APPLICATION_JSON));
        server.expect(requestTo(REVOKE_URI)).andRespond(withServerError());

        assertFailsWith(AuthErrorCode.APPLE_UNAVAILABLE);
    }

    @Test
    void 다른_사용자의_code는_철회하지_않는다() {
        server.expect(requestTo(TOKEN_URI)).andRespond(withSuccess(
                "{\"refresh_token\":\"apple-refresh\",\"id_token\":\"other-id\"}", MediaType.APPLICATION_JSON));
        assertFailsWith(AuthErrorCode.APPLE_REAUTHENTICATION_FAILED);
        server.verify();
    }

    @Test
    void 교환_응답에_identity_token이_없으면_철회하지_않는다() {
        server.expect(requestTo(TOKEN_URI)).andRespond(withSuccess(
                "{\"refresh_token\":\"apple-refresh\"}", MediaType.APPLICATION_JSON));
        assertFailsWith(AuthErrorCode.APPLE_UNAVAILABLE);
        server.verify();
    }

    private void assertFailsWith(AuthErrorCode errorCode) {
        assertThatThrownBy(() -> client.revoke(new AppleReauthentication("apple-sub", BUNDLE_ID), "auth-code"))
                .isInstanceOfSatisfying(BusinessException.class, exception ->
                        assertThat(exception.getErrorCode()).isEqualTo(errorCode));
    }

    private static MultiValueMap<String, String> form(String... keyValues) {
        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        for (int i = 0; i < keyValues.length; i += 2) {
            form.add(keyValues[i], keyValues[i + 1]);
        }
        return form;
    }
}
