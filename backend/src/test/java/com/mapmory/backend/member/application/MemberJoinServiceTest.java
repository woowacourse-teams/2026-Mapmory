package com.mapmory.backend.member.application;

import static org.assertj.core.api.Assertions.assertThat;

import com.mapmory.backend.IntegrationTest;
import com.mapmory.backend.member.AuthProvider;
import com.mapmory.backend.member.Member;
import com.mapmory.backend.member.MemberRepository;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

class MemberJoinServiceTest extends IntegrationTest {

    @Autowired
    private MemberJoinService memberJoinService;

    @Autowired
    private MemberRepository memberRepository;

    @Test
    void 처음_보는_소셜_계정이면_새_회원으로_가입한다() {
        long memberCountBefore = memberRepository.count();

        SocialJoin join = memberJoinService.joinWithSocial(AuthProvider.KAKAO, "300001", "소현", null);

        assertThat(join.type()).isEqualTo(SocialJoinType.SOCIAL_JOINED);
        assertThat(join.leftGuest()).isNull();
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
        assertThat(join.leftGuest()).isNull();
        assertThat(join.member().getId()).isEqualTo(guest.getId());
        assertThat(memberRepository.count()).isEqualTo(memberCountBefore);
        Member promoted = memberRepository.findById(guest.getId()).orElseThrow();
        assertThat(promoted.getProvider()).isEqualTo(AuthProvider.GOOGLE);
        assertThat(promoted.getProviderId()).isEqualTo("google-sub");
        assertThat(promoted.getName()).isEqualTo("소현");
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
    void 이미_가입한_소셜_계정이면_기존_회원으로_들어오고_게스트를_돌려준다() {
        Member registered = memberJoinService.joinWithSocial(AuthProvider.APPLE, "apple-sub", null, null).member();
        Member guest = memberJoinService.joinAsGuest();

        SocialJoin join = memberJoinService.joinWithSocial(AuthProvider.APPLE, "apple-sub", null, guest.getId());

        assertThat(join.type()).isEqualTo(SocialJoinType.EXISTING_ACCOUNT);
        assertThat(join.member().getId()).isEqualTo(registered.getId());
        assertThat(join.leftGuest().getId()).isEqualTo(guest.getId());
        assertThat(memberRepository.findById(guest.getId()).orElseThrow().getProvider())
                .isEqualTo(AuthProvider.GUEST);
    }

    @Test
    void 게스트_없이_이미_가입한_소셜_계정이면_돌려줄_게스트가_없다() {
        Member registered = memberJoinService.joinWithSocial(AuthProvider.KAKAO, "300002", "소현", null).member();

        SocialJoin join = memberJoinService.joinWithSocial(AuthProvider.KAKAO, "300002", "소현", null);

        assertThat(join.type()).isEqualTo(SocialJoinType.EXISTING_ACCOUNT);
        assertThat(join.member().getId()).isEqualTo(registered.getId());
        assertThat(join.leftGuest()).isNull();
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
