package com.mapmory.backend.auth.infrastructure.kakao;

import com.mapmory.backend.auth.exception.AuthErrorCode;
import com.mapmory.backend.auth.infrastructure.kakao.config.KakaoProperties;
import com.mapmory.backend.common.exception.BusinessException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

/**
 * 카카오 사용자 정보 API 호출.
 *
 * 앱이 전달한 카카오 access token이 우리 앱에서 발급됐는지 GET /v1/user/access_token_info 로 확인한 뒤,
 * GET /v2/user/me 를 호출해 회원 정보를 가져온다.
 * 카카오 access token은 발급받은 앱과 무관하게 사용자 정보 API에 쓸 수 있어, 확인하지 않으면
 * 다른 카카오 앱이 받은 토큰으로 그 사용자로 로그인할 수 있다(토큰 치환). (ADR 0010)
 *
 * 오류를 구분한다.
 *   - 카카오 4xx (토큰 만료·위조 등, 사용자 책임) → INVALID_KAKAO_TOKEN (401, 재로그인)
 *   - 다른 앱에서 발급된 토큰 → INVALID_KAKAO_TOKEN (401)
 *   - 카카오 5xx / 응답 없음 / 회원번호·앱 ID 없는 응답 (카카오 장애) → KAKAO_UNAVAILABLE (503, 재시도)
 */
@Component
class KakaoApiClient {

    private static final Logger log = LoggerFactory.getLogger(KakaoApiClient.class);

    private final RestClient restClient;
    private final String tokenInfoUri;
    private final String userInfoUri;
    private final Long appId;

    KakaoApiClient(RestClient kakaoRestClient, KakaoProperties kakaoProperties) {
        this.restClient = kakaoRestClient;
        this.tokenInfoUri = kakaoProperties.tokenInfoUri();
        this.userInfoUri = kakaoProperties.userInfoUri();
        this.appId = kakaoProperties.appId();
    }

    KakaoUserResponse fetchUser(String kakaoAccessToken) {
        verifyIssuedForUs(kakaoAccessToken);
        KakaoUserResponse response = request(userInfoUri, kakaoAccessToken, KakaoUserResponse.class);
        if (response == null || response.id() == null) {
            // 회원번호 없이 provider_id를 만들면 서로 다른 사람이 한 회원으로 묶이므로 로그인시키지 않는다.
            throw new BusinessException(AuthErrorCode.KAKAO_UNAVAILABLE);
        }
        return response;
    }

    // 앱 ID 설정이 비어 있으면 어떤 토큰과도 같지 않아 모든 카카오 로그인이 거부된다.
    private void verifyIssuedForUs(String kakaoAccessToken) {
        KakaoTokenInfoResponse tokenInfo = request(tokenInfoUri, kakaoAccessToken, KakaoTokenInfoResponse.class);
        if (tokenInfo == null || tokenInfo.appId() == null) {
            throw new BusinessException(AuthErrorCode.KAKAO_UNAVAILABLE);
        }
        if (!tokenInfo.appId().equals(appId)) {
            // 앱 ID 설정 불일치와 토큰 치환 시도를 운영 로그에서 구분할 수 있도록 남긴다. 앱 ID는 비밀값이 아니다.
            log.info("다른 앱에서 발급된 카카오 토큰: appId={}", tokenInfo.appId());
            throw new BusinessException(AuthErrorCode.INVALID_KAKAO_TOKEN);
        }
    }

    private <T> T request(String uri, String kakaoAccessToken, Class<T> responseType) {
        try {
            return restClient.get()
                    .uri(uri)
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + kakaoAccessToken)
                    .retrieve()
                    .body(responseType);
        } catch (RestClientResponseException exception) {
            if (exception.getStatusCode().is5xxServerError()) {
                throw kakaoUnavailable(exception);
            }
            throw new BusinessException(AuthErrorCode.INVALID_KAKAO_TOKEN);
        } catch (RestClientException exception) {
            // 연결 실패 등 응답 자체를 받지 못한 경우도 카카오 장애로 취급한다.
            throw kakaoUnavailable(exception);
        }
    }

    private static BusinessException kakaoUnavailable(RestClientException cause) {
        return new BusinessException(
                AuthErrorCode.KAKAO_UNAVAILABLE,
                AuthErrorCode.KAKAO_UNAVAILABLE.detail(),
                cause
        );
    }
}
