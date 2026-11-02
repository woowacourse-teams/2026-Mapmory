package com.mapmory.backend.auth.infrastructure.apple;

import com.mapmory.backend.auth.exception.AuthErrorCode;
import com.mapmory.backend.auth.infrastructure.apple.config.AppleProperties;
import com.mapmory.backend.common.exception.BusinessException;
import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.ECDSASigner;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import java.security.GeneralSecurityException;
import java.security.KeyFactory;
import java.security.interfaces.ECPrivateKey;
import java.security.spec.PKCS8EncodedKeySpec;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.Date;
import org.springframework.stereotype.Component;

/**
 * 애플 토큰 API에 보낼 client_secret을 만든다.
 *
 * client_secret은 Sign in with Apple 키(.p8)로 서명한 ES256 JWT다. (ADR 0025)
 *   - 헤더 kid : 키 ID
 *   - iss      : 팀 ID
 *   - sub      : client_id (iOS 앱은 번들 ID)
 *   - aud      : https://appleid.apple.com
 *   - exp      : 애플은 최대 6개월을 허용하지만 요청할 때마다 만들므로 짧게 둔다
 *
 * 키 설정이 없으면 로그인은 되지만 철회는 할 수 없어 APPLE_UNAVAILABLE로 실패한다.
 */
@Component
class AppleClientSecretFactory {

    private static final String AUDIENCE = "https://appleid.apple.com";
    private static final Duration VALIDITY = Duration.ofMinutes(5);

    private final String teamId;
    private final String keyId;
    private final ECPrivateKey privateKey;

    AppleClientSecretFactory(AppleProperties properties) {
        this.teamId = properties.teamId();
        this.keyId = properties.keyId();
        this.privateKey = properties.canRevoke() ? parsePrivateKey(properties.privateKey()) : null;
    }

    String create(String clientId) {
        if (privateKey == null) {
            // 설정 누락은 응답에 드러내지 않고, 원인만 로그(ERROR 알림)에 남긴다.
            throw new BusinessException(
                    AuthErrorCode.APPLE_UNAVAILABLE,
                    AuthErrorCode.APPLE_UNAVAILABLE.detail(),
                    new IllegalStateException("애플 토큰 철회 키(APPLE_TEAM_ID·APPLE_KEY_ID·APPLE_PRIVATE_KEY)가 설정되지 않았습니다.")
            );
        }
        Instant now = Instant.now();
        JWTClaimsSet claims = new JWTClaimsSet.Builder()
                .issuer(teamId)
                .subject(clientId)
                .audience(AUDIENCE)
                .issueTime(Date.from(now))
                .expirationTime(Date.from(now.plus(VALIDITY)))
                .build();
        SignedJWT jwt = new SignedJWT(new JWSHeader.Builder(JWSAlgorithm.ES256).keyID(keyId).build(), claims);
        try {
            jwt.sign(new ECDSASigner(privateKey));
        } catch (JOSEException exception) {
            throw new IllegalStateException("애플 client_secret에 서명하지 못했습니다.", exception);
        }
        return jwt.serialize();
    }

    /**
     * .p8 내용(PKCS#8 PEM)을 읽는다. 환경변수 한 줄에 넣을 수 있도록 줄바꿈 대신 \n 문자열도 받고,
     * BEGIN/END 줄 없이 본문만 넣어도 된다. 키가 잘못됐으면 애플리케이션이 뜰 때 실패시킨다.
     */
    private static ECPrivateKey parsePrivateKey(String pem) {
        String body = pem.replace("\\n", "\n")
                .replace("-----BEGIN PRIVATE KEY-----", "")
                .replace("-----END PRIVATE KEY-----", "")
                .replaceAll("\\s", "");
        try {
            byte[] der = Base64.getDecoder().decode(body);
            return (ECPrivateKey) KeyFactory.getInstance("EC").generatePrivate(new PKCS8EncodedKeySpec(der));
        } catch (IllegalArgumentException | GeneralSecurityException | ClassCastException exception) {
            throw new IllegalStateException("apple.private-key(APPLE_PRIVATE_KEY)를 EC 개인 키로 읽지 못했습니다.", exception);
        }
    }
}
