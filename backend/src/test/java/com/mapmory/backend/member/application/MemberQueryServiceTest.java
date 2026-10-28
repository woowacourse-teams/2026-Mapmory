package com.mapmory.backend.member.application;

import static org.assertj.core.api.Assertions.assertThat;

import com.mapmory.backend.IntegrationTest;
import com.mapmory.backend.member.Member;
import com.mapmory.backend.member.MemberRepository;
import com.mapmory.backend.member.api.MemberQuery;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

class MemberQueryServiceTest extends IntegrationTest {

    @Autowired
    private MemberQuery memberQuery;

    @Autowired
    private MemberRepository memberRepository;

    @Test
    void 없는_id는_빠지고_탈퇴한_회원은_탈퇴_닉네임이다() {
        Member jisu = memberRepository.save(Member.of("지수", UUID.randomUUID()));
        Member withdrawn = Member.of("민호", UUID.randomUUID());
        withdrawn.withdraw(LocalDateTime.of(2026, 10, 1, 0, 0));
        memberRepository.save(withdrawn);

        assertThat(memberQuery.nicknamesOf(List.of(jisu.getId(), withdrawn.getId(), Long.MAX_VALUE)))
                .containsOnlyKeys(jisu.getId(), withdrawn.getId())
                .containsEntry(jisu.getId(), jisu.getNickname())
                .containsEntry(withdrawn.getId(), "탈퇴한 회원");
    }
}
