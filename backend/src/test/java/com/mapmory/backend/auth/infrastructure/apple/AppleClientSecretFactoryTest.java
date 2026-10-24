package com.mapmory.backend.auth.infrastructure.apple;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.mapmory.backend.auth.exception.AuthErrorCode;
import com.mapmory.backend.auth.infrastructure.apple.config.AppleProperties;
import com.mapmory.backend.common.exception.BusinessException;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.crypto.ECDSAVerifier;
import com.nimbusds.jwt.SignedJWT;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.interfaces.ECPublicKey;
import java.security.spec.ECGenParameterSpec;
import java.util.Base64;
import java.util.Date;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class AppleClientSecretFactoryTest {

    private static final String TEAM_ID = "TEAM123456";
    private static final String KEY_ID = "KEY1234567";
    private static final String BUNDLE_ID = "com.mapmory.ios";

    private KeyPair appleKey;

    @BeforeEach
    void setUp() throws Exception {
        KeyPairGenerator generator = KeyPairGenerator.getInstance("EC");
        generator.initialize(new ECGenParameterSpec("secp256r1"));
        appleKey = generator.generateKeyPair();
    }

    @Test
    void 키로_서명한_ES256_client_secret을_만든다() throws Exception {
        AppleClientSecretFactory factory = new AppleClientSecretFactory(properties(pem(appleKey)));

        SignedJWT secret = SignedJWT.parse(factory.create(BUNDLE_ID));

        assertThat(secret.verify(new ECDSAVerifier((ECPublicKey) appleKey.getPublic()))).isTrue();
        assertThat(secret.getHeader().getAlgorithm()).isEqualTo(JWSAlgorithm.ES256);
        assertThat(secret.getHeader().getKeyID()).isEqualTo(KEY_ID);
        assertThat(secret.getJWTClaimsSet().getIssuer()).isEqualTo(TEAM_ID);
        assertThat(secret.getJWTClaimsSet().getSubject()).isEqualTo(BUNDLE_ID);
        assertThat(secret.getJWTClaimsSet().getAudience()).containsExactly("https://appleid.apple.com");
        assertThat(secret.getJWTClaimsSet().getExpirationTime()).isAfter(new Date());
    }

    @Test
    void 한_줄로_넣은_키와_본문만_넣은_키도_읽는다() throws Exception {
        String pem = pem(appleKey);
        String oneLine = pem.replace("\n", "\\n");
        String bodyOnly = Base64.getEncoder().encodeToString(appleKey.getPrivate().getEncoded());

        for (String key : List.of(oneLine, bodyOnly)) {
            SignedJWT secret = SignedJWT.parse(new AppleClientSecretFactory(properties(key)).create(BUNDLE_ID));
            assertThat(secret.verify(new ECDSAVerifier((ECPublicKey) appleKey.getPublic()))).isTrue();
        }
    }

    @Test
    void 키가_설정되지_않으면_APPLE_UNAVAILABLE이다() {
        AppleClientSecretFactory factory = new AppleClientSecretFactory(properties(null));

        assertThatThrownBy(() -> factory.create(BUNDLE_ID))
                .isInstanceOfSatisfying(BusinessException.class, exception ->
                        assertThat(exception.getErrorCode()).isEqualTo(AuthErrorCode.APPLE_UNAVAILABLE));
    }

    @Test
    void 키를_읽지_못하면_뜰_때_실패한다() {
        assertThatThrownBy(() -> new AppleClientSecretFactory(properties("not-a-key")))
                .isInstanceOf(IllegalStateException.class);
    }

    private static AppleProperties properties(String privateKey) {
        return new AppleProperties(List.of(BUNDLE_ID), "https://appleid.apple.com/auth/keys",
                TEAM_ID, KEY_ID, privateKey, "https://appleid.apple.com/auth/token", "https://appleid.apple.com/auth/revoke");
    }

    private static String pem(KeyPair keyPair) {
        String body = Base64.getMimeEncoder(64, "\n".getBytes()).encodeToString(keyPair.getPrivate().getEncoded());
        return "-----BEGIN PRIVATE KEY-----\n" + body + "\n-----END PRIVATE KEY-----";
    }
}
