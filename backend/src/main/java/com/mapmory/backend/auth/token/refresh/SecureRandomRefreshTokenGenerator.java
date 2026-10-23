package com.mapmory.backend.auth.token.refresh;

import java.security.SecureRandom;
import java.util.Base64;
import org.springframework.stereotype.Component;

/**
 * SecureRandom 32바이트를 Base64URL로 인코딩한 불투명 문자열. (ADR 0011)
 */
@Component
class SecureRandomRefreshTokenGenerator implements RefreshTokenGenerator {

    private static final int RAW_TOKEN_BYTES = 32;

    private final SecureRandom secureRandom = new SecureRandom();

    @Override
    public String generate() {
        byte[] bytes = new byte[RAW_TOKEN_BYTES];
        secureRandom.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }
}
