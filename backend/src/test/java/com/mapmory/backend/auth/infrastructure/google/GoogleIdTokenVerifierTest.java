package com.mapmory.backend.auth.infrastructure.google;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.mapmory.backend.auth.exception.AuthErrorCode;
import com.mapmory.backend.auth.infrastructure.google.config.GoogleIdTokenValidators;
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

class GoogleIdTokenVerifierTest {

    private static final String CLIENT_ID = "mapmory-web.apps.googleusercontent.com";
    private static final String ISSUER = "https://accounts.google.com";

    private KeyPair googleKey;
    private GoogleIdTokenVerifier verifier;

    @BeforeEach
    void setUp() throws NoSuchAlgorithmException {
        googleKey = rsaKeyPair();
        // 운영은 JWKS URI로 공개키를 받지만, 검증 규칙(validator)은 운영과 같은 것을 쓴다.
        NimbusJwtDecoder decoder = NimbusJwtDecoder
                .withPublicKey((RSAPublicKey) googleKey.getPublic())
                .build();
        decoder.setJwtValidator(GoogleIdTokenValidators.create(List.of(CLIENT_ID)));
        verifier = new GoogleIdTokenVerifier(decoder);
    }

    @Test
    void 유효한_ID_token이면_sub와_이름을_반환한다() throws Exception {
        String idToken = sign(googleKey, claims().build());

        GoogleUser user = verifier.verify(idToken);

        assertThat(user.subject()).isEqualTo("109876543210");
        assertThat(user.name()).isEqualTo("소현");
    }

    @Test
    void 스킴_없는_issuer도_허용한다() throws Exception {
        String idToken = sign(googleKey, claims().issuer("accounts.google.com").build());

        assertThat(verifier.verify(idToken).subject()).isEqualTo("109876543210");
    }

    @Test
    void 다른_앱용으로_발급된_토큰은_INVALID_GOOGLE_TOKEN이다() throws Exception {
        String idToken = sign(googleKey, claims().audience("other-app.apps.googleusercontent.com").build());

        assertInvalid(idToken);
    }

    @Test
    void 구글이_아닌_issuer는_INVALID_GOOGLE_TOKEN이다() throws Exception {
        String idToken = sign(googleKey, claims().issuer("https://evil.example.com").build());

        assertInvalid(idToken);
    }

    @Test
    void 만료된_토큰은_INVALID_GOOGLE_TOKEN이다() throws Exception {
        Instant past = Instant.now().minusSeconds(3_600);
        String idToken = sign(googleKey, claims()
                .issueTime(Date.from(past.minusSeconds(3_600)))
                .expirationTime(Date.from(past))
                .build());

        assertInvalid(idToken);
    }

    @Test
    void 다른_키로_서명된_토큰은_INVALID_GOOGLE_TOKEN이다() throws Exception {
        String idToken = sign(rsaKeyPair(), claims().build());

        assertInvalid(idToken);
    }

    @Test
    void sub가_없거나_비어_있으면_INVALID_GOOGLE_TOKEN이다() throws Exception {
        assertInvalid(sign(googleKey, claims().subject(null).build()));
        assertInvalid(sign(googleKey, claims().subject(" ").build()));
    }

    @Test
    void exp가_없는_토큰은_INVALID_GOOGLE_TOKEN이다() throws Exception {
        String idToken = sign(googleKey, claims().expirationTime(null).build());

        assertInvalid(idToken);
    }

    @Test
    void aud가_여러_개면_azp가_우리_클라이언트일_때만_허용한다() throws Exception {
        List<String> audiences = List.of(CLIENT_ID, "other-app.apps.googleusercontent.com");

        assertInvalid(sign(googleKey, claims().audience(audiences).build()));
        assertInvalid(sign(googleKey, claims().audience(audiences)
                .claim("azp", "other-app.apps.googleusercontent.com").build()));
        assertThat(verifier.verify(sign(googleKey, claims().audience(audiences).claim("azp", CLIENT_ID).build()))
                .subject()).isEqualTo("109876543210");
    }

    @Test
    void 허용된_클라이언트_ID가_없으면_모든_토큰을_거부한다() throws Exception {
        NimbusJwtDecoder decoder = NimbusJwtDecoder
                .withPublicKey((RSAPublicKey) googleKey.getPublic())
                .build();
        decoder.setJwtValidator(GoogleIdTokenValidators.create(List.of()));
        GoogleIdTokenVerifier unconfigured = new GoogleIdTokenVerifier(decoder);
        String idToken = sign(googleKey, claims().build());

        assertThatThrownBy(() -> unconfigured.verify(idToken))
                .isInstanceOfSatisfying(BusinessException.class, exception ->
                        assertThat(exception.getErrorCode()).isEqualTo(AuthErrorCode.INVALID_GOOGLE_TOKEN));
    }

    @Test
    void 형식이_잘못된_토큰은_INVALID_GOOGLE_TOKEN이다() {
        assertInvalid("not-a-jwt");
    }

    @Test
    void 공개키를_받아오지_못하면_GOOGLE_UNAVAILABLE이다() {
        GoogleIdTokenVerifier unavailable = new GoogleIdTokenVerifier(token -> {
            throw new JwtException("An error occurred while attempting to decode the Jwt: connection refused");
        });

        assertThatThrownBy(() -> unavailable.verify("token"))
                .isInstanceOfSatisfying(BusinessException.class, exception -> {
                    assertThat(exception.getErrorCode()).isEqualTo(AuthErrorCode.GOOGLE_UNAVAILABLE);
                    assertThat(exception.getCause()).isInstanceOf(JwtException.class);
                });
    }

    private void assertInvalid(String idToken) {
        assertThatThrownBy(() -> verifier.verify(idToken))
                .isInstanceOfSatisfying(BusinessException.class, exception ->
                        assertThat(exception.getErrorCode()).isEqualTo(AuthErrorCode.INVALID_GOOGLE_TOKEN));
    }

    private static JWTClaimsSet.Builder claims() {
        Instant now = Instant.now();
        return new JWTClaimsSet.Builder()
                .issuer(ISSUER)
                .audience(CLIENT_ID)
                .subject("109876543210")
                .claim("name", "소현")
                .issueTime(Date.from(now))
                .expirationTime(Date.from(now.plusSeconds(3_600)));
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
