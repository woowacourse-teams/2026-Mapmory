package com.mapmory.backend.member;

import static org.assertj.core.api.Assertions.assertThat;

import com.mapmory.backend.support.MySqlTestContainerSupport;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.data.domain.PageRequest;

@DataJpaTest
class MemberRepositoryTest extends MySqlTestContainerSupport {

    @Autowired
    private MemberRepository memberRepository;

    @Test
    void 이름과_닉네임을_함께_저장하고_읽는다() {
        Member member = Member.ofOAuth(AuthProvider.KAKAO, "600001", "소현", UUID.randomUUID());
        member.changeNickname("지수");
        memberRepository.saveAndFlush(member);

        Member found = memberRepository.findById(member.getId()).orElseThrow();

        assertThat(found.getName()).isEqualTo("소현");
        assertThat(found.getNickname()).isEqualTo("지수");
    }

    @Test
    void 관리자_검색은_닉네임이_아닌_이름으로_찾는다() {
        Member member = Member.ofOAuth(AuthProvider.KAKAO, "600002", "김소현", UUID.randomUUID());
        member.changeNickname("여행가지수");
        memberRepository.saveAndFlush(member);

        assertThat(memberRepository.searchForAdmin(null, "소현", null, PageRequest.of(0, 10)).getContent())
                .extracting(Member::getId)
                .containsExactly(member.getId());
        assertThat(memberRepository.searchForAdmin(null, "여행가", null, PageRequest.of(0, 10)).getContent())
                .isEmpty();
    }
}
