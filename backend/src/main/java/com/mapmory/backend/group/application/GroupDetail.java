package com.mapmory.backend.group.application;

import java.util.List;

/**
 * 그룹 화면에 보여 줄 그룹과 멤버. 멤버는 들어온 순서이고 회원 id는 담지 않는다.
 */
public record GroupDetail(Long id, String name, List<MemberView> members) {

    public record MemberView(String nickname, boolean owner, boolean me) {
    }
}
