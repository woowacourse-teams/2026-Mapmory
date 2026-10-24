package com.mapmory.backend.member.application;

import com.mapmory.backend.member.AuthProvider;
import com.mapmory.backend.member.Member;
import com.mapmory.backend.member.MemberName;
import com.mapmory.backend.member.MemberRepository;
import com.mapmory.backend.member.event.GuestAbandoned;
import com.mapmory.backend.member.event.GuestPromoted;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 로그인할 때 어떤 회원으로 들어오는가를 정한다. 토큰은 다루지 않는다.
 */
@Service
public class MemberJoinService {

    private final MemberRepository memberRepository;
    private final ApplicationEventPublisher eventPublisher;
    private final Clock clock;

    public MemberJoinService(MemberRepository memberRepository, ApplicationEventPublisher eventPublisher, Clock clock) {
        this.memberRepository = memberRepository;
        this.eventPublisher = eventPublisher;
        this.clock = clock;
    }

    /**
     * 소셜 계정으로 들어온다.
     *
     * 게스트로 사용 중이었다면(guestMemberId가 활동 중인 게스트 회원) 새 회원을 만들지 않고
     * 그 회원을 승격해, 게스트로 남긴 기록이 그대로 이어지게 한다. 게스트일 때의 세션을 끝내도록 GuestPromoted를 발행한다. (#460)
     * 탈퇴했거나 버려진 게스트의 남은 access token이 오면 게스트가 없는 것으로 보고 일반 로그인으로 처리한다. (ADR 0021)
     *
     * 이미 같은 소셜 계정으로 가입한 회원이 있으면 한 사람에게 회원 행이 둘인 상황이므로,
     * 기존 회원을 우선하고 guestAbandoned로 게스트를 두고 떠났음을 알린다. 이때 게스트가 남긴 기록은 이어지지 않는다.
     * (ADR 0015) 게스트는 버려진 게스트로 표시하고 GuestAbandoned를 발행한다. (ADR 0021)
     */
    @Transactional
    public SocialJoin joinWithSocial(AuthProvider provider, String providerId, String providerName, Long guestMemberId) {
        Optional<Member> guest = findGuest(guestMemberId);

        Optional<Member> registered = memberRepository.findByProviderAndProviderId(provider, providerId);
        if (registered.isPresent()) {
            guest.ifPresent(this::abandon);
            return new SocialJoin(registered.get(), SocialJoinType.EXISTING_ACCOUNT, guest.isPresent());
        }

        if (guest.isPresent()) {
            Member promoted = guest.get();
            promoted.promote(provider, providerId, providerName, LocalDateTime.now(clock));
            eventPublisher.publishEvent(new GuestPromoted(promoted.getId(), promoted.getPromotedAt()));
            return new SocialJoin(promoted, SocialJoinType.PROMOTED, false);
        }

        Member joined = memberRepository.save(Member.ofOAuth(
                provider,
                providerId,
                MemberName.fromProvider(providerName),
                UUID.randomUUID()
        ));
        return new SocialJoin(joined, SocialJoinType.SOCIAL_JOINED, false);
    }

    /**
     * 로그인 없이 서비스를 사용하기 위한 게스트 회원을 만든다.
     *
     * 소셜 로그인과 달리 외부에 물어볼 신원이 없으므로 식별자를 서버가 발급한다. (ADR 0015)
     */
    @Transactional
    public Member joinAsGuest() {
        return memberRepository.save(Member.ofGuest(
                UUID.randomUUID().toString(),
                MemberName.generated(),
                UUID.randomUUID()
        ));
    }

    private void abandon(Member guest) {
        guest.abandon(LocalDateTime.now(clock));
        eventPublisher.publishEvent(new GuestAbandoned(guest.getId(), guest.getAbandonedAt()));
    }

    private Optional<Member> findGuest(Long memberId) {
        if (memberId == null) {
            return Optional.empty();
        }
        return memberRepository.findById(memberId)
                .filter(member -> member.getProvider() == AuthProvider.GUEST)
                .filter(Member::isActive);
    }
}
