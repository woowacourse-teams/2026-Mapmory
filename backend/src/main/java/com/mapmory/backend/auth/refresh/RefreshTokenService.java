package com.mapmory.backend.auth.refresh;

import com.mapmory.backend.auth.exception.AuthErrorCode;
import com.mapmory.backend.auth.jwt.JwtProperties;
import com.mapmory.backend.common.exception.BusinessException;
import com.mapmory.backend.member.Member;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.HexFormat;
import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * refresh 토큰 발급·회전·폐기.
 *
 * 원문(raw)은 SecureRandom으로 생성해 클라이언트에만 반환하고, 서버는 SHA-256 해시만 저장한다.
 * 회전 시 기존 토큰을 폐기하고, 이미 폐기된 토큰이 다시 제시되면(탈취 의심) 해당 회원의
 * 유효한 토큰을 모두 폐기한다.
 */
@Service
public class RefreshTokenService {

    private static final int RAW_TOKEN_BYTES = 32;

    private final RefreshTokenRepository refreshTokenRepository;
    private final Duration refreshTokenValidity;
    private final SecureRandom secureRandom = new SecureRandom();

    public RefreshTokenService(
            RefreshTokenRepository refreshTokenRepository,
            JwtProperties jwtProperties
    ) {
        this.refreshTokenRepository = refreshTokenRepository;
        this.refreshTokenValidity = jwtProperties.refreshTokenValidity();
    }

    @Transactional
    public String issue(Member member) {
        String rawToken = generateRawToken();
        LocalDateTime expiresAt = LocalDateTime.now().plus(refreshTokenValidity);
        refreshTokenRepository.save(RefreshToken.issue(member, hash(rawToken), expiresAt));
        return rawToken;
    }

    /**
     * 회전을 위한 검증.
     *
     * 토큰 행에 비관적 락(FOR UPDATE)을 걸어 같은 토큰 동시 사용의 이중 회전을 막는다.
     * 반환값으로 결과를 구분한다.
     *   - 유효: 기존 토큰을 폐기하고 소유 회원을 담아 반환(Optional.of)
     *   - 재사용(이미 폐기된 토큰): 탈취로 간주해 회원의 유효 토큰을 모두 폐기하고 Optional.empty 반환
     *     (폐기를 커밋해야 하므로 여기서 예외를 던지지 않는다. 401 변환은 호출부가 한다.)
     *   - 없음/만료: 남길 부작용이 없으므로 예외로 던진다.
     */
    @Transactional
    public Optional<Member> validateAndRevoke(String rawToken) {
        RefreshToken refreshToken = refreshTokenRepository.findByTokenHashForUpdate(hash(rawToken))
                .orElseThrow(() -> new BusinessException(AuthErrorCode.INVALID_REFRESH_TOKEN));

        LocalDateTime now = LocalDateTime.now();
        if (refreshToken.isRevoked()) {
            refreshTokenRepository.revokeAllActiveByMember(refreshToken.getMember(), now);
            return Optional.empty();
        }
        if (refreshToken.isExpired(now)) {
            throw new BusinessException(AuthErrorCode.EXPIRED_REFRESH_TOKEN);
        }

        refreshToken.revoke(now);
        return Optional.of(refreshToken.getMember());
    }

    @Transactional
    public void revoke(String rawToken) {
        refreshTokenRepository.findByTokenHash(hash(rawToken))
                .ifPresent(token -> token.revoke(LocalDateTime.now()));
    }

    private String generateRawToken() {
        byte[] bytes = new byte[RAW_TOKEN_BYTES];
        secureRandom.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private String hash(String rawToken) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(rawToken.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 알고리즘을 사용할 수 없습니다.", exception);
        }
    }
}
