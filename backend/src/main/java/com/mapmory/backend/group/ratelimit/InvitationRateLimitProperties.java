package com.mapmory.backend.group.ratelimit;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 회원별 초대 미리보기·수락 한도. 두 API가 같은 한도를 나눠 쓴다.
 */
@ConfigurationProperties(prefix = "group.invitation.rate-limit")
record InvitationRateLimitProperties(
        int perMemberPerMinute,
        int perMemberPerDay
) {
    InvitationRateLimitProperties {
        if (perMemberPerMinute < 1 || perMemberPerDay < perMemberPerMinute) {
            throw new IllegalArgumentException("초대 회원 한도 설정이 올바르지 않습니다.");
        }
    }
}
