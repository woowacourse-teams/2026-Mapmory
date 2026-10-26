package com.mapmory.backend.group.application;

import java.security.SecureRandom;
import java.util.Base64;
import org.springframework.stereotype.Component;

/**
 * 초대 링크용 토큰과 직접 입력용 6자리 코드를 만든다.
 *
 * <p>토큰은 SecureRandom 32바이트를 Base64URL로 인코딩한 43자다(리프레시 토큰과 같은 방식, ADR 0011).
 * 코드는 손으로 옮겨 적기 쉽게 헷갈리는 글자(0·O, 1·I)를 뺀 대문자와 숫자 32개에서 고른다.
 */
@Component
public class InvitationKeyGenerator {

    static final String CODE_ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";
    static final int CODE_LENGTH = 6;
    private static final int TOKEN_BYTES = 32;

    private final SecureRandom secureRandom = new SecureRandom();

    public String token() {
        byte[] bytes = new byte[TOKEN_BYTES];
        secureRandom.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    public String code() {
        StringBuilder code = new StringBuilder(CODE_LENGTH);
        for (int i = 0; i < CODE_LENGTH; i++) {
            code.append(CODE_ALPHABET.charAt(secureRandom.nextInt(CODE_ALPHABET.length())));
        }
        return code.toString();
    }
}
