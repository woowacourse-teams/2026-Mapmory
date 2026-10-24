package com.mapmory.backend.auth.infrastructure.kakao;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.mapmory.backend.auth.exception.AuthErrorCode;
import com.mapmory.backend.auth.infrastructure.kakao.config.KakaoIdTokenValidators;
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

class KakaoIdTokenVerifierTest {

    private static final String NATIVE_APP_KEY = "kakao-native-app-key";
    private static final String ISSUER = "https://kauth.kakao.com";

    private KeyPair kakaoKey;
    private KakaoIdTokenVerifier verifier;

    @BeforeEach
    void setUp() throws NoSuchAlgorithmException {
        kakaoKey = rsaKeyPair();
        // 운영은 JWKS URI로 공개키를 받지만, 검증 규칙(validator)은 운영과 같은 것을 쓴다.
        verifier = verifierAllowing(List.of(NATIVE_APP_KEY));
    }

    @Test
    void 유효한_ID_token이면_회원번호와_닉네임을_반환한다() throws Exception {
        String idToken = sign(kakaoKey, claims().claim("nickname", "소현").build());

        KakaoUser user = verifier.verify(idToken);

        assertThat(user.subject()).isEqualTo("12345");
        assertThat(user.nickname()).isEqualTo("소현");
    }

    @Test
    void 닉네임에_동의하지_않아_nickname이_없으면_닉네임_없이_반환한다() throws Exception {
        KakaoUser user = verifier.verify(sign(kakaoKey, claims().build()));

        assertThat(user.subject()).isEqualTo("12345");
        assertThat(user.nickname()).isNull();
    }

    @Test
    void 다른_앱용으로_발급된_토큰은_INVALID_KAKAO_TOKEN이다() throws Exception {
        assertInvalid(sign(kakaoKey, claims().audience("other-app-key").build()));
    }

    @Test
    void 카카오가_아닌_issuer는_INVALID_KAKAO_TOKEN이다() throws Exception {
        assertInvalid(sign(kakaoKey, claims().issuer("https://evil.example.com").build()));
        assertInvalid(sign(kakaoKey, claims().issuer("kauth.kakao.com").build()));
    }

    @Test
    void 만료된_토큰은_INVALID_KAKAO_TOKEN이다() throws Exception {
        Instant past = Instant.now().minusSeconds(3_600);
        String idToken = sign(kakaoKey, claims()
                .issueTime(Date.from(past.minusSeconds(3_600)))
                .expirationTime(Date.from(past))
                .build());

        assertInvalid(idToken);
    }

    @Test
    void exp가_없는_토큰은_INVALID_KAKAO_TOKEN이다() throws Exception {
        assertInvalid(sign(kakaoKey, claims().expirationTime(null).build()));
    }

    @Test
    void 다른_키로_서명된_토큰은_INVALID_KAKAO_TOKEN이다() throws Exception {
        assertInvalid(sign(rsaKeyPair(), claims().build()));
    }

    @Test
    void sub가_없거나_비어_있으면_INVALID_KAKAO_TOKEN이다() throws Exception {
        assertInvalid(sign(kakaoKey, claims().subject(null).build()));
        assertInvalid(sign(kakaoKey, claims().subject(" ").build()));
    }

    @Test
    void 허용된_클라이언트_ID가_없으면_모든_토큰을_거부한다() throws Exception {
        KakaoIdTokenVerifier unconfigured = verifierAllowing(List.of());
        String idToken = sign(kakaoKey, claims().build());

        assertThatThrownBy(() -> unconfigured.verify(idToken))
                .isInstanceOfSatisfying(BusinessException.class, exception ->
                        assertThat(exception.getErrorCode()).isEqualTo(AuthErrorCode.INVALID_KAKAO_TOKEN));
    }

    @Test
    void 형식이_잘못된_토큰은_INVALID_KAKAO_TOKEN이다() {
        assertInvalid("not-a-jwt");
    }

    @Test
    void 공개키를_받아오지_못하면_KAKAO_UNAVAILABLE이다() {
        KakaoIdTokenVerifier unavailable = new KakaoIdTokenVerifier(token -> {
            throw new JwtException("An error occurred while attempting to decode the Jwt: connection refused");
        });

        assertThatThrownBy(() -> unavailable.verify("token"))
                .isInstanceOfSatisfying(BusinessException.class, exception -> {
                    assertThat(exception.getErrorCode()).isEqualTo(AuthErrorCode.KAKAO_UNAVAILABLE);
                    assertThat(exception.getCause()).isInstanceOf(JwtException.class);
                });
    }

    private KakaoIdTokenVerifier verifierAllowing(List<String> clientIds) {
        NimbusJwtDecoder decoder = NimbusJwtDecoder
                .withPublicKey((RSAPublicKey) kakaoKey.getPublic())
                .build();
        decoder.setJwtValidator(KakaoIdTokenValidators.create(clientIds));
        return new KakaoIdTokenVerifier(decoder);
    }

    private void assertInvalid(String idToken) {
        assertThatThrownBy(() -> verifier.verify(idToken))
                .isInstanceOfSatisfying(BusinessException.class, exception ->
                        assertThat(exception.getErrorCode()).isEqualTo(AuthErrorCode.INVALID_KAKAO_TOKEN));
    }

    private static JWTClaimsSet.Builder claims() {
        Instant now = Instant.now();
        return new JWTClaimsSet.Builder()
                .issuer(ISSUER)
                .audience(NATIVE_APP_KEY)
                .subject("12345")
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
