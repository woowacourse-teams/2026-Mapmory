package com.mapmory.backend.group.application;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 그룹 화면에 보여 줄 그룹과 멤버. 멤버는 들어온 순서다.
 *
 * <p>닉네임은 겹칠 수 있어 회원 id로 구분한다. 앨범의 담은 사람(AlbumDetail.Contributor)과 같은 값이다.
 */
public record GroupDetail(Long id, String name, List<MemberView> members) {

    public record MemberView(Long memberId, String nickname, boolean owner, boolean me, LocalDateTime joinedAt) {
    }
}
