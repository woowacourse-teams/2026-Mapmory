package com.mapmory.backend.auth.kakao;

import com.mapmory.backend.auth.exception.AuthErrorCode;
import com.mapmory.backend.common.exception.BusinessException;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

/**
 * 카카오 사용자 정보 API 호출.
 *
 * 앱이 전달한 카카오 access token으로 GET /v2/user/me 를 호출해 회원 정보를 가져온다.
 *
 * 오류를 구분한다.
 *   - 카카오 4xx (토큰 만료·위조 등, 사용자 책임) → INVALID_KAKAO_TOKEN (401, 재로그인)
 *   - 카카오 5xx / 응답 없음 (카카오 장애) → KAKAO_UNAVAILABLE (503, 재시도)
 */
@Component
public class KakaoApiClient {

    private final RestClient restClient;
    private final String userInfoUri;

    public KakaoApiClient(RestClient kakaoRestClient, KakaoProperties kakaoProperties) {
        this.restClient = kakaoRestClient;
        this.userInfoUri = kakaoProperties.userInfoUri();
    }

    public KakaoUserResponse fetchUser(String kakaoAccessToken) {
        try {
            return restClient.get()
                    .uri(userInfoUri)
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + kakaoAccessToken)
                    .retrieve()
                    .body(KakaoUserResponse.class);
        } catch (RestClientResponseException exception) {
            if (exception.getStatusCode().is5xxServerError()) {
                throw new BusinessException(AuthErrorCode.KAKAO_UNAVAILABLE);
            }
            throw new BusinessException(AuthErrorCode.INVALID_KAKAO_TOKEN);
        } catch (RestClientException exception) {
            // 연결 실패 등 응답 자체를 받지 못한 경우도 카카오 장애로 취급한다.
            throw new BusinessException(AuthErrorCode.KAKAO_UNAVAILABLE);
        }
    }
}
