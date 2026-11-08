package com.mapmory.backend.admin.dashboard;

import static org.assertj.core.api.Assertions.assertThat;

import com.mapmory.backend.IntegrationTest;
import com.mapmory.backend.admin.dashboard.dto.AdminDashboardResponse.MemberStatistics;
import com.mapmory.backend.member.AuthProvider;
import com.mapmory.backend.member.Member;
import com.mapmory.backend.member.MemberRepository;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * 회원 행은 탈퇴하거나 버려져도 남으므로 회원 수에서 따로 뺀다. (#550)
 */
class AdminDashboardMemberCountIntegrationTest extends IntegrationTest {

    private static final LocalDateTime NOW = LocalDateTime.now(ZoneOffset.UTC);

    @Autowired
    private AdminDashboardService service;

    @Autowired
    private MemberRepository memberRepository;

    @Test
    void 회원_수에서_탈퇴_회원과_버려진_게스트를_뺀다() {
        var before = members();

        memberRepository.save(Member.ofOAuth(AuthProvider.KAKAO, "550001", "활동", UUID.randomUUID()));
        memberRepository.save(Member.ofGuest("guest-550001", "게스트", UUID.randomUUID()));
        Member withdrawn = Member.ofOAuth(AuthProvider.KAKAO, "550002", "탈퇴", UUID.randomUUID());
        withdrawn.withdraw(NOW);
        memberRepository.save(withdrawn);
        Member abandoned = Member.ofGuest("guest-550002", "버려진", UUID.randomUUID());
        abandoned.abandon(NOW);
        memberRepository.save(abandoned);

        var after = members();

        assertThat(after.total() - before.total()).isEqualTo(2);
        assertThat(after.kakao() - before.kakao()).isEqualTo(1);
        assertThat(after.guest() - before.guest()).isEqualTo(1);
    }

    // 기간 내 가입 수는 가입이라는 사건을 세므로 나중에 탈퇴해도 줄지 않는다.
    @Test
    void 기간_내_가입_수에는_탈퇴한_회원도_남는다() {
        var before = members();

        Member withdrawn = Member.ofOAuth(AuthProvider.KAKAO, "550003", "탈퇴", UUID.randomUUID());
        withdrawn.withdraw(NOW);
        memberRepository.save(withdrawn);

        assertThat(members().newInPeriod() - before.newInPeriod()).isEqualTo(1);
    }

    // 로컬 마이그레이션이 넣은 임시 회원이 있을 수 있어 늘어난 수만 본다.
    private MemberStatistics members() {
        return service.getDashboard(null, null).members();
    }
}
