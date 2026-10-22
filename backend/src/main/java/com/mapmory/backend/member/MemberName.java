package com.mapmory.backend.member;

import com.mapmory.backend.common.exception.BusinessException;
import com.mapmory.backend.member.exception.MemberErrorCode;
import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import java.util.Objects;
import java.util.concurrent.ThreadLocalRandom;

/**
 * 회원의 이름과 닉네임.
 *
 * name     : 소셜 제공자가 준 이름. 없으면 기본 이름이다. 회원이 바꾸지 않고 관리자 화면에서만 쓴다.
 * nickname : 다른 회원에게 보이는 이름. 제공자 이름과 관계없이 기본 이름으로 시작하고, 로그인으로 바뀌지 않는다.
 *            회원이 직접 바꿀 때만 바뀐다.
 *
 * DB에서 읽을 때는 값이 있는지만 확인한다. 닉네임 규칙이 생기기 전에 저장된 이름이 있을 수 있어서다.
 * 닉네임 규칙은 회원이 닉네임을 바꿀 때 확인한다.
 */
@Embeddable
public record MemberName(
        @Column(name = "name", nullable = false, length = 50)
        String name,

        @Column(name = "nickname", nullable = false, length = 20)
        String nickname
) {

    private static final String DEFAULT_NAME_PREFIX = "회원";
    // member.name 컬럼 길이(VARCHAR(50))와 같다.
    private static final int MAX_NAME_LENGTH = 50;
    private static final int MIN_NICKNAME_LENGTH = 1;
    // member.nickname 컬럼 길이(VARCHAR(20))와 같다.
    private static final int MAX_NICKNAME_LENGTH = 20;

    public MemberName {
        Objects.requireNonNull(name, "name must not be null");
        Objects.requireNonNull(nickname, "nickname must not be null");
    }

    /**
     * 가입할 때의 이름과 닉네임. 닉네임은 이름과 관계없이 기본 이름으로 시작한다.
     */
    static MemberName startingWith(String name) {
        return new MemberName(name, generated());
    }

    /**
     * 기본 이름. 처음 닉네임과, 닉네임 미동의나 게스트처럼 이름이 없을 때의 이름에 쓴다. (예: 회원58213)
     */
    public static String generated() {
        return DEFAULT_NAME_PREFIX + ThreadLocalRandom.current().nextInt(10_000, 100_000);
    }

    /**
     * 소셜 제공자가 준 이름. 비어 있으면 기본 이름을 쓴다.
     */
    public static String fromProvider(String providerName) {
        if (isBlank(providerName)) {
            return generated();
        }
        return truncate(providerName);
    }

    /**
     * 승격할 때. 제공자가 이름을 주면 이름을 바꾸고, 주지 않으면(애플 등) 게스트일 때의 이름을 그대로 쓴다.
     * 닉네임은 바뀌지 않는다.
     */
    MemberName promote(String providerName) {
        if (isBlank(providerName)) {
            return this;
        }
        return new MemberName(truncate(providerName), nickname);
    }

    /**
     * 회원이 직접 정한 닉네임으로 바꾼다. 앞뒤 공백을 지운 뒤 1~20자(코드 포인트)여야 하고 제어 문자는 넣을 수 없다.
     * 이름과 달리 자르지 않고 거절한다. 다른 회원과 같아도 된다. 이름은 그대로다.
     */
    MemberName changeNickname(String rawNickname) {
        return new MemberName(name, validateNickname(rawNickname));
    }

    private static String validateNickname(String rawNickname) {
        if (rawNickname == null) {
            throw invalidNickname();
        }
        String nickname = rawNickname.strip();
        int length = nickname.codePointCount(0, nickname.length());
        if (length < MIN_NICKNAME_LENGTH || length > MAX_NICKNAME_LENGTH || hasControlCharacter(nickname)) {
            throw invalidNickname();
        }
        return nickname;
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    // 구글 표시 이름은 길이 제한이 없어 컬럼(50자)을 넘을 수 있다. 서로게이트 쌍이 잘리지 않게 코드 포인트 단위로 자른다.
    private static String truncate(String name) {
        if (name.codePointCount(0, name.length()) <= MAX_NAME_LENGTH) {
            return name;
        }
        return name.substring(0, name.offsetByCodePoints(0, MAX_NAME_LENGTH));
    }

    private static boolean hasControlCharacter(String value) {
        return value.codePoints().anyMatch(Character::isISOControl);
    }

    private static BusinessException invalidNickname() {
        return new BusinessException(MemberErrorCode.INVALID_NICKNAME);
    }
}
