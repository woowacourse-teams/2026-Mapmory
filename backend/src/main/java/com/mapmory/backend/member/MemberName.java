package com.mapmory.backend.member;

import java.util.concurrent.ThreadLocalRandom;

/**
 * 회원 이름 규칙.
 *
 * 이름(name)은 소셜 제공자가 준 이름이다. 없으면 구분 가능한 기본 이름을 짓고, 있으면 컬럼 길이에 맞게 자른다.
 * 닉네임(nickname)은 다른 회원에게 보이는 이름이다. 제공자 이름과 관계없이 기본 이름으로 시작한다.
 */
public final class MemberName {

    private static final String DEFAULT_NAME_PREFIX = "회원";
    // member.name 컬럼 길이(VARCHAR(50))와 같다.
    private static final int MAX_NAME_LENGTH = 50;

    private MemberName() {
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
        if (providerName == null || providerName.isBlank()) {
            return generated();
        }
        return truncate(providerName);
    }

    // 구글 표시 이름은 길이 제한이 없어 컬럼(50자)을 넘을 수 있다. 서로게이트 쌍이 잘리지 않게 코드 포인트 단위로 자른다.
    private static String truncate(String name) {
        if (name.codePointCount(0, name.length()) <= MAX_NAME_LENGTH) {
            return name;
        }
        return name.substring(0, name.offsetByCodePoints(0, MAX_NAME_LENGTH));
    }
}
