package com.mapmory.backend.member.api;

import java.util.Collection;
import java.util.Map;

/**
 * 다른 컨텍스트가 회원을 읽는 창구. 읽기만 하고 엔티티 대신 값을 넘긴다(ADR 0017).
 */
public interface MemberQuery {

    /**
     * id → 닉네임. 없는 id는 결과에서 빠진다. 탈퇴한 회원은 행이 남아 있어 탈퇴할 때 바꾼 닉네임("탈퇴한 회원")을 준다.
     */
    Map<Long, String> nicknamesOf(Collection<Long> memberIds);
}
