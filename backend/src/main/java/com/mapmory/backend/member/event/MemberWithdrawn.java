package com.mapmory.backend.member.event;

import com.mapmory.backend.member.AuthProvider;
import java.time.LocalDateTime;

/**
 * 회원이 탈퇴했다. (E09, ADR 0021)
 *
 * 회원 행은 남기고(soft) 이름·닉네임·providerId만 지운 상태다. 회원의 다른 데이터는 각 컨텍스트가 이 이벤트를 받아 지운다.
 * 엔티티를 담지 않는다. 받는 쪽은 memberId로 자기 데이터를 찾는다.
 *
 * 받는 쪽
 *   - auth: 회원의 refresh 토큰을 모두 폐기한다. 탈퇴와 같은 트랜잭션에서 실행돼야 하므로 @EventListener(동기)다.
 *   - notification: 회원의 기기 토큰을 모두 지운다. 탈퇴가 커밋된 뒤에 받는다. (InactiveMemberListener)
 *   - group: 회원이 속한 모든 그룹에서 나간다. 탈퇴가 커밋된 뒤에 받는다. (MemberWithdrawnListener)
 *   - 여행기록·미디어 등 다른 컨텍스트는 탈퇴 30일 뒤 파기한다(#435). (아직 없음)
 *
 * memberId    : 탈퇴한 회원
 * provider    : 어떤 로그인 방식이었는지 (GUEST 포함)
 * withdrawnAt : 탈퇴 시각
 */
public record MemberWithdrawn(
        Long memberId,
        AuthProvider provider,
        LocalDateTime withdrawnAt
) {
}
