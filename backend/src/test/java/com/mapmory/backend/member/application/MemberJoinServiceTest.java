package com.mapmory.backend.member.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import com.mapmory.backend.IntegrationTest;
import com.mapmory.backend.member.AuthProvider;
import com.mapmory.backend.member.Member;
import com.mapmory.backend.member.MemberRepository;
import com.mapmory.backend.member.event.GuestAbandoned;
import com.mapmory.backend.member.event.GuestPromoted;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.event.ApplicationEvents;
import org.springframework.test.context.event.RecordApplicationEvents;

@RecordApplicationEvents
class MemberJoinServiceTest extends IntegrationTest {

    @Autowired
    private MemberJoinService memberJoinService;

    @Autowired
    private MemberRepository memberRepository;

    @Autowired
    private ApplicationEvents events;

    @Test
    void 처음_보는_소셜_계정이면_새_회원으로_가입한다() {
        long memberCountBefore = memberRepository.count();

        SocialJoin join = memberJoinService.joinWithSocial(AuthProvider.KAKAO, "300001", "소현", null);

        assertThat(join.type()).isEqualTo(SocialJoinType.SOCIAL_JOINED);
        assertThat(join.guestAbandoned()).isFalse();
        assertThat(join.member().getProvider()).isEqualTo(AuthProvider.KAKAO);
        assertThat(join.member().getProviderId()).isEqualTo("300001");
        assertThat(join.member().getName()).isEqualTo("소현");
        assertThat(memberRepository.count()).isEqualTo(memberCountBefore + 1);
    }

    @Test
    void 게스트가_처음_보는_소셜_계정으로_들어오면_그_게스트를_승격한다() {
        Member guest = memberJoinService.joinAsGuest();
        long memberCountBefore = memberRepository.count();

        SocialJoin join = memberJoinService.joinWithSocial(AuthProvider.GOOGLE, "google-sub", "소현", guest.getId());

        assertThat(join.type()).isEqualTo(SocialJoinType.PROMOTED);
        assertThat(join.guestAbandoned()).isFalse();
        assertThat(join.member().getId()).isEqualTo(guest.getId());
        assertThat(memberRepository.count()).isEqualTo(memberCountBefore);
        Member promoted = memberRepository.findById(guest.getId()).orElseThrow();
        assertThat(promoted.getProvider()).isEqualTo(AuthProvider.GOOGLE);
        assertThat(promoted.getProviderId()).isEqualTo("google-sub");
        assertThat(promoted.getName()).isEqualTo("소현");
        assertThat(promoted.getPromotedAt()).isNotNull();
    }

    @Test
    void 닉네임을_바꾼_게스트가_승격해도_그_닉네임을_유지한다() {
        Member guest = memberJoinService.joinAsGuest();
        Member changed = memberRepository.findById(guest.getId()).orElseThrow();
        changed.changeNickname("지수");
        memberRepository.save(changed);

        memberJoinService.joinWithSocial(AuthProvider.KAKAO, "300010", "소현", guest.getId());

        Member promoted = memberRepository.findById(guest.getId()).orElseThrow();
        assertThat(promoted.getName()).isEqualTo("소현");
        assertThat(promoted.getNickname()).isEqualTo("지수");
    }

    @Test
    void 이미_가입한_소셜_계정이면_기존_회원으로_들어오고_게스트를_두고_떠났음을_알린다() {
        Member registered = memberJoinService.joinWithSocial(AuthProvider.APPLE, "apple-sub", null, null).member();
        Member guest = memberJoinService.joinAsGuest();

        SocialJoin join = memberJoinService.joinWithSocial(AuthProvider.APPLE, "apple-sub", null, guest.getId());

        assertThat(join.type()).isEqualTo(SocialJoinType.EXISTING_ACCOUNT);
        assertThat(join.member().getId()).isEqualTo(registered.getId());
        assertThat(join.guestAbandoned()).isTrue();
        assertThat(memberRepository.findById(guest.getId()).orElseThrow().getProvider())
                .isEqualTo(AuthProvider.GUEST);
    }

    @Test
    void 게스트_없이_이미_가입한_소셜_계정이면_두고_떠난_게스트가_없다() {
        Member registered = memberJoinService.joinWithSocial(AuthProvider.KAKAO, "300002", "소현", null).member();

        SocialJoin join = memberJoinService.joinWithSocial(AuthProvider.KAKAO, "300002", "소현", null);

        assertThat(join.type()).isEqualTo(SocialJoinType.EXISTING_ACCOUNT);
        assertThat(join.member().getId()).isEqualTo(registered.getId());
        assertThat(join.guestAbandoned()).isFalse();
    }

    @Test
    void 게스트가_아닌_회원_ID가_오면_승격하지_않고_새로_가입한다() {
        Member social = memberJoinService.joinWithSocial(AuthProvider.KAKAO, "300003", "카카오 회원", null).member();

        SocialJoin join = memberJoinService.joinWithSocial(AuthProvider.GOOGLE, "google-sub-2", "구글 회원", social.getId());

        assertThat(join.type()).isEqualTo(SocialJoinType.SOCIAL_JOINED);
        assertThat(join.member().getId()).isNotEqualTo(social.getId());
        Member untouched = memberRepository.findById(social.getId()).orElseThrow();
        assertThat(untouched.getProvider()).isEqualTo(AuthProvider.KAKAO);
        assertThat(untouched.getProviderId()).isEqualTo("300003");
    }

    @Test
    void 탈퇴한_게스트의_ID가_오면_승격하지_않고_새로_가입한다() {
        Member guest = memberJoinService.joinAsGuest();
        Member withdrawn = memberRepository.findById(guest.getId()).orElseThrow();
        withdrawn.withdraw(LocalDateTime.of(2026, 10, 22, 15, 0));
        memberRepository.save(withdrawn);

        SocialJoin join = memberJoinService.joinWithSocial(AuthProvider.KAKAO, "300020", "소현", guest.getId());

        assertThat(join.type()).isEqualTo(SocialJoinType.SOCIAL_JOINED);
        assertThat(join.member().getId()).isNotEqualTo(guest.getId());
        assertThat(join.guestAbandoned()).isFalse();
        Member untouched = memberRepository.findById(guest.getId()).orElseThrow();
        assertThat(untouched.isWithdrawn()).isTrue();
        assertThat(untouched.getProviderId()).isNull();
        assertThat(untouched.getName()).isEqualTo("탈퇴한 회원");
    }

    @Test
    void 이미_가입한_소셜_계정으로_들어오면_두고_떠난_게스트를_버려진_게스트로_표시하고_이벤트를_발행한다() {
        memberJoinService.joinWithSocial(AuthProvider.KAKAO, "300030", "소현", null);
        Member guest = memberJoinService.joinAsGuest();

        memberJoinService.joinWithSocial(AuthProvider.KAKAO, "300030", "소현", guest.getId());

        Member abandoned = memberRepository.findById(guest.getId()).orElseThrow();
        assertThat(abandoned.isAbandoned()).isTrue();
        assertThat(abandoned.isActive()).isFalse();
        assertThat(events.stream(GuestAbandoned.class))
                .singleElement()
                .satisfies(event -> {
                    assertThat(event.guestMemberId()).isEqualTo(guest.getId());
                    // DATETIME 컬럼은 초 단위로 저장되므로 1초 안쪽 차이는 같은 시각으로 본다.
                    assertThat(event.abandonedAt()).isCloseTo(abandoned.getAbandonedAt(), within(1, ChronoUnit.SECONDS));
                });
    }

    @Test
    void 게스트_없이_이미_가입한_소셜_계정으로_들어오면_이벤트를_발행하지_않는다() {
        memberJoinService.joinWithSocial(AuthProvider.KAKAO, "300031", "소현", null);

        memberJoinService.joinWithSocial(AuthProvider.KAKAO, "300031", "소현", null);

        assertThat(events.stream(GuestAbandoned.class)).isEmpty();
    }

    @Test
    void 버려진_게스트의_ID로_다시_들어와도_이벤트를_다시_발행하지_않는다() {
        memberJoinService.joinWithSocial(AuthProvider.KAKAO, "300032", "소현", null);
        Member guest = memberJoinService.joinAsGuest();
        memberJoinService.joinWithSocial(AuthProvider.KAKAO, "300032", "소현", guest.getId());

        SocialJoin join = memberJoinService.joinWithSocial(AuthProvider.KAKAO, "300032", "소현", guest.getId());

        assertThat(join.type()).isEqualTo(SocialJoinType.EXISTING_ACCOUNT);
        assertThat(join.guestAbandoned()).isFalse();
        assertThat(events.stream(GuestAbandoned.class)).hasSize(1);
    }

    @Test
    void 버려진_게스트의_ID로_처음_보는_소셜_계정이_들어오면_승격하지_않고_새로_가입한다() {
        memberJoinService.joinWithSocial(AuthProvider.KAKAO, "300033", "소현", null);
        Member guest = memberJoinService.joinAsGuest();
        memberJoinService.joinWithSocial(AuthProvider.KAKAO, "300033", "소현", guest.getId());

        SocialJoin join = memberJoinService.joinWithSocial(AuthProvider.GOOGLE, "google-sub-3", "소현", guest.getId());

        assertThat(join.type()).isEqualTo(SocialJoinType.SOCIAL_JOINED);
        assertThat(join.member().getId()).isNotEqualTo(guest.getId());
        Member untouched = memberRepository.findById(guest.getId()).orElseThrow();
        assertThat(untouched.getProvider()).isEqualTo(AuthProvider.GUEST);
        assertThat(untouched.isAbandoned()).isTrue();
    }

    @Test
    void 승격하면_GuestPromoted를_발행한다() {
        Member guest = memberJoinService.joinAsGuest();

        memberJoinService.joinWithSocial(AuthProvider.KAKAO, "300036", "소현", guest.getId());

        assertThat(events.stream(GuestPromoted.class))
                .singleElement()
                .satisfies(event -> {
                    assertThat(event.memberId()).isEqualTo(guest.getId());
                    assertThat(event.promotedAt()).isNotNull();
                });
    }

    // promoted_at - created_at으로 승격까지 걸린 시간을 센다(V30). 두 시각이 같은 기준(UTC)이어야 한다. (#502)
    @Test
    void 승격_시각과_버려진_시각은_가입_시각과_같은_기준으로_저장한다() {
        Member guest = memberJoinService.joinAsGuest();
        memberJoinService.joinWithSocial(AuthProvider.KAKAO, "300037", "소현", guest.getId());
        Member abandonedGuest = memberJoinService.joinAsGuest();
        memberJoinService.joinWithSocial(AuthProvider.KAKAO, "300037", "소현", abandonedGuest.getId());

        Member promoted = memberRepository.findById(guest.getId()).orElseThrow();
        Member abandoned = memberRepository.findById(abandonedGuest.getId()).orElseThrow();
        assertThat(promoted.getPromotedAt()).isCloseTo(promoted.getCreatedAt(), within(1, ChronoUnit.MINUTES));
        assertThat(abandoned.getAbandonedAt()).isCloseTo(abandoned.getCreatedAt(), within(1, ChronoUnit.MINUTES));
    }

    @Test
    void 새로_가입하거나_기존_회원으로_들어오면_GuestPromoted를_발행하지_않는다() {
        memberJoinService.joinWithSocial(AuthProvider.KAKAO, "300037", "소현", null);
        Member guest = memberJoinService.joinAsGuest();
        memberJoinService.joinWithSocial(AuthProvider.KAKAO, "300037", "소현", guest.getId());

        assertThat(events.stream(GuestPromoted.class)).isEmpty();
    }

    @Test
    void 승격하거나_새로_가입하면_GuestAbandoned를_발행하지_않는다() {
        Member guest = memberJoinService.joinAsGuest();

        memberJoinService.joinWithSocial(AuthProvider.KAKAO, "300034", "소현", guest.getId());
        memberJoinService.joinWithSocial(AuthProvider.KAKAO, "300035", "소현", null);

        assertThat(events.stream(GuestAbandoned.class)).isEmpty();
    }

    @Test
    void 이름이_없으면_기본_이름으로_가입한다() {
        SocialJoin join = memberJoinService.joinWithSocial(AuthProvider.APPLE, "apple-sub-2", null, null);

        assertThat(join.member().getName()).matches("회원[1-9]\\d{4}");
    }

    @Test
    void 게스트는_서버가_발급한_식별자와_기본_이름으로_가입한다() {
        Member guest = memberJoinService.joinAsGuest();

        assertThat(guest.getProvider()).isEqualTo(AuthProvider.GUEST);
        assertThat(UUID.fromString(guest.getProviderId())).isNotNull();
        assertThat(guest.getName()).matches("회원[1-9]\\d{4}");
    }
}
