package com.mapmory.backend.auth.infrastructure.apple;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.mapmory.backend.auth.exception.AuthErrorCode;
import com.mapmory.backend.auth.infrastructure.apple.config.AppleProperties;
import com.mapmory.backend.common.exception.BusinessException;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

/**
 * 애플 토큰 API 호출. 탈퇴할 때 앱이 받은 authorization code를 토큰으로 바꾸고, 그 토큰을 바로 철회한다. (ADR 0025)
 *
 * 오류를 구분한다.
 *   - code가 만료(5분)됐거나 이미 쓰였음 (invalid_grant, 사용자가 다시 재인증하면 됨) → APPLE_REAUTHENTICATION_FAILED (400)
 *   - 그 밖의 거절(client_secret·client_id 설정 오류 등), 애플 5xx, 응답 없음 → APPLE_UNAVAILABLE (503, ERROR 알림)
 */
@Component
class AppleTokenClient {

    private static final String INVALID_GRANT = "invalid_grant";

    private final RestClient restClient;
    private final AppleClientSecretFactory clientSecretFactory;
    private final String tokenUri;
    private final String revokeUri;

    AppleTokenClient(RestClient appleRestClient, AppleClientSecretFactory clientSecretFactory, AppleProperties properties) {
        this.restClient = appleRestClient;
        this.clientSecretFactory = clientSecretFactory;
        this.tokenUri = properties.tokenUri();
        this.revokeUri = properties.revokeUri();
    }

    /**
     * code를 토큰으로 바꾼 뒤 철회한다. 애플은 refresh token을 철회하면 그 사용자와 앱의 연결을 끊는다.
     * 교환 응답에 refresh token이 없으면 access token을 철회한다.
     */
    void revoke(String clientId, String authorizationCode) {
        String clientSecret = clientSecretFactory.create(clientId);
        AppleTokenResponse tokens = exchange(clientId, clientSecret, authorizationCode);
        if (tokens.refreshToken() != null) {
            revokeToken(clientId, clientSecret, tokens.refreshToken(), "refresh_token");
            return;
        }
        revokeToken(clientId, clientSecret, tokens.accessToken(), "access_token");
    }

    private AppleTokenResponse exchange(String clientId, String clientSecret, String authorizationCode) {
        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("client_id", clientId);
        form.add("client_secret", clientSecret);
        form.add("code", authorizationCode);
        form.add("grant_type", "authorization_code");
        try {
            AppleTokenResponse response = post(tokenUri, form).body(AppleTokenResponse.class);
            if (response == null || (response.refreshToken() == null && response.accessToken() == null)) {
                throw unavailable(new IllegalStateException("애플 토큰 교환 응답에 토큰이 없습니다."));
            }
            return response;
        } catch (RestClientResponseException exception) {
            if (isInvalidGrant(exception)) {
                throw new BusinessException(AuthErrorCode.APPLE_REAUTHENTICATION_FAILED);
            }
            throw unavailable(exception);
        } catch (RestClientException exception) {
            throw unavailable(exception);
        }
    }

    private void revokeToken(String clientId, String clientSecret, String token, String tokenTypeHint) {
        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("client_id", clientId);
        form.add("client_secret", clientSecret);
        form.add("token", token);
        form.add("token_type_hint", tokenTypeHint);
        try {
            post(revokeUri, form).toBodilessEntity();
        } catch (RestClientException exception) {
            // 방금 받은 토큰이라 철회가 거절될 이유가 없다. 거절이든 장애든 탈퇴를 멈추고 알림을 받는다.
            throw unavailable(exception);
        }
    }

    private RestClient.ResponseSpec post(String uri, MultiValueMap<String, String> form) {
        return restClient.post()
                .uri(uri)
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .body(form)
                .retrieve();
    }

    private static boolean isInvalidGrant(RestClientResponseException exception) {
        return exception.getStatusCode().isSameCodeAs(HttpStatus.BAD_REQUEST)
                && exception.getResponseBodyAsString().contains(INVALID_GRANT);
    }

    private static BusinessException unavailable(Exception cause) {
        return new BusinessException(AuthErrorCode.APPLE_UNAVAILABLE, AuthErrorCode.APPLE_UNAVAILABLE.detail(), cause);
    }

    /**
     * 애플 POST /auth/token 응답 중 철회에 쓰는 값만 매핑한다. 토큰 원문은 로그에 남기지 않는다.
     */
    record AppleTokenResponse(
            @JsonProperty("access_token") String accessToken,
            @JsonProperty("refresh_token") String refreshToken
    ) {
    }
}
