package com.mapmory.backend.auth.infrastructure.apple;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.mapmory.backend.auth.exception.AuthErrorCode;
import com.mapmory.backend.auth.infrastructure.apple.config.AppleIdentityTokenValidators;
import com.mapmory.backend.common.exception.BusinessException;
import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.NoSuchAlgorithmException;
import java.security.interfaces.RSAPublicKey;
import java.time.Instant;
import java.util.Date;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;

class AppleIdentityTokenVerifierTest {

    private static final String BUNDLE_ID = "com.mapmory.ios";
    private static final String ISSUER = "https://appleid.apple.com";

    private KeyPair appleKey;
    private AppleIdentityTokenVerifier verifier;

    @BeforeEach
    void setUp() throws NoSuchAlgorithmException {
        appleKey = rsaKeyPair();
        // 운영은 JWKS URI로 공개키를 받지만, 검증 규칙(validator)은 운영과 같은 것을 쓴다.
        verifier = verifierAllowing(List.of(BUNDLE_ID));
    }

    @Test
    void 유효한_identity_token이면_sub를_반환한다() throws Exception {
        String identityToken = sign(appleKey, claims().build());

        assertThat(verifier.verify(identityToken).subject()).isEqualTo("001234.abcdef0123456789.0123");
    }

    @Test
    void 다른_앱용으로_발급된_토큰은_INVALID_APPLE_TOKEN이다() throws Exception {
        assertInvalid(sign(appleKey, claims().audience("com.other.app").build()));
    }

    @Test
    void 애플이_아닌_issuer는_INVALID_APPLE_TOKEN이다() throws Exception {
        assertInvalid(sign(appleKey, claims().issuer("https://evil.example.com").build()));
        assertInvalid(sign(appleKey, claims().issuer("appleid.apple.com").build()));
    }

    @Test
    void 만료된_토큰은_INVALID_APPLE_TOKEN이다() throws Exception {
        Instant past = Instant.now().minusSeconds(3_600);
        String identityToken = sign(appleKey, claims()
                .issueTime(Date.from(past.minusSeconds(3_600)))
                .expirationTime(Date.from(past))
                .build());

        assertInvalid(identityToken);
    }

    @Test
    void exp가_없는_토큰은_INVALID_APPLE_TOKEN이다() throws Exception {
        assertInvalid(sign(appleKey, claims().expirationTime(null).build()));
    }

    @Test
    void 다른_키로_서명된_토큰은_INVALID_APPLE_TOKEN이다() throws Exception {
        assertInvalid(sign(rsaKeyPair(), claims().build()));
    }

    @Test
    void sub가_없거나_비어_있으면_INVALID_APPLE_TOKEN이다() throws Exception {
        assertInvalid(sign(appleKey, claims().subject(null).build()));
        assertInvalid(sign(appleKey, claims().subject(" ").build()));
    }

    @Test
    void 허용된_클라이언트_ID가_없으면_모든_토큰을_거부한다() throws Exception {
        AppleIdentityTokenVerifier unconfigured = verifierAllowing(List.of());
        String identityToken = sign(appleKey, claims().build());

        assertThatThrownBy(() -> unconfigured.verify(identityToken))
                .isInstanceOfSatisfying(BusinessException.class, exception ->
                        assertThat(exception.getErrorCode()).isEqualTo(AuthErrorCode.INVALID_APPLE_TOKEN));
    }

    @Test
    void 형식이_잘못된_토큰은_INVALID_APPLE_TOKEN이다() {
        assertInvalid("not-a-jwt");
    }

    @Test
    void 공개키를_받아오지_못하면_APPLE_UNAVAILABLE이다() {
        AppleIdentityTokenVerifier unavailable = new AppleIdentityTokenVerifier(token -> {
            throw new JwtException("An error occurred while attempting to decode the Jwt: connection refused");
        });

        assertThatThrownBy(() -> unavailable.verify("token"))
                .isInstanceOfSatisfying(BusinessException.class, exception -> {
                    assertThat(exception.getErrorCode()).isEqualTo(AuthErrorCode.APPLE_UNAVAILABLE);
                    assertThat(exception.getCause()).isInstanceOf(JwtException.class);
                });
    }

    private AppleIdentityTokenVerifier verifierAllowing(List<String> clientIds) {
        NimbusJwtDecoder decoder = NimbusJwtDecoder
                .withPublicKey((RSAPublicKey) appleKey.getPublic())
                .build();
        decoder.setJwtValidator(AppleIdentityTokenValidators.create(clientIds));
        return new AppleIdentityTokenVerifier(decoder);
    }

    private void assertInvalid(String identityToken) {
        assertThatThrownBy(() -> verifier.verify(identityToken))
                .isInstanceOfSatisfying(BusinessException.class, exception ->
                        assertThat(exception.getErrorCode()).isEqualTo(AuthErrorCode.INVALID_APPLE_TOKEN));
    }

    private static JWTClaimsSet.Builder claims() {
        Instant now = Instant.now();
        return new JWTClaimsSet.Builder()
                .issuer(ISSUER)
                .audience(BUNDLE_ID)
                .subject("001234.abcdef0123456789.0123")
                .issueTime(Date.from(now))
                .expirationTime(Date.from(now.plusSeconds(600)));
    }

    private static String sign(KeyPair keyPair, JWTClaimsSet claims) throws JOSEException {
        SignedJWT jwt = new SignedJWT(new JWSHeader(JWSAlgorithm.RS256), claims);
        jwt.sign(new RSASSASigner(keyPair.getPrivate()));
        return jwt.serialize();
    }

    private static KeyPair rsaKeyPair() throws NoSuchAlgorithmException {
        KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
        generator.initialize(2048);
        return generator.generateKeyPair();
    }
}
