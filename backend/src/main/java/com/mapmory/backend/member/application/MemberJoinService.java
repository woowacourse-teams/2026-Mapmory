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

        Optional<Member> registered = findRegistered(provider, providerId);
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
     * 이 소셜 계정으로 가입한 회원이 있는지 확인한다.
     *
     * 동시에 들어온 첫 로그인이 무결성 위반으로 실패했을 때, 그것이 같은 소셜 계정의 먼저 가입한 회원 때문인지
     * 판단하는 데 쓴다. 예외의 제약 이름을 해석하지 않으므로 DB 방언이나 제약 이름에 묶이지 않는다. (#461)
     * 호출 시점에 실패한 트랜잭션은 이미 롤백되어 끝났으므로, 그 상태에 영향받지 않고 조회한다.
     */
    public boolean hasSocialAccount(AuthProvider provider, String providerId) {
        return memberRepository.existsByProviderAndProviderId(provider, providerId);
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

    /**
     * 승격하거나 버릴 게스트를 잠그고 읽는다.
     *
     * 같은 게스트가 소셜 로그인을 연타하면 두 요청이 모두 활동 중인 게스트로 읽고 각자 승격한다. UPDATE끼리라
     * UNIQUE 위반이 나지 않아 AuthService의 재시도에도 걸리지 않는다. 늦은 쪽의 승격이 refresh를 모두 폐기하면서
     * 먼저 끝난 쪽이 방금 발급한 refresh까지 폐기하거나, 둘이 refresh를 폐기·발급하다 데드락이 난다. (#574)
     * 잠그면 늦은 쪽은 먼저 온 쪽의 커밋을 기다렸다가 승격된 회원을 읽어 게스트 없음이 되고, 기존 회원으로 들어온다.
     *
     * joinWithSocial의 첫 조회여야 한다. MySQL REPEATABLE READ는 첫 일반 조회에서 스냅샷을 만들므로,
     * 이 잠금 조회가 먼저여야 뒤의 기존 회원 조회(findRegistered)가 먼저 온 쪽의 커밋을 본다.
     */
    /**
     * 이 소셜 계정으로 가입한 활동 중인 회원을 공유 잠금으로 읽는다.
     *
     * 잠그지 않고 읽으면, 읽은 뒤 그 회원의 탈퇴가 먼저 커밋되어도 읽은 결과로 그 회원에게 토큰을 발급하고
     * 게스트를 버린다. 탈퇴한 회원에게 쓸 수 있는 refresh가 남는다. (#584)
     * 잠그면 진행 중인 탈퇴의 커밋을 기다렸다가 최신 행을 읽는다. 탈퇴했으면 기존 회원이 없는 것으로 보아,
     * 게스트는 승격되고 게스트가 없으면 새로 가입한다. 탈퇴 뒤 다시 가입하는 것과 같다.
     *
     * 반대로 이 잠금 뒤에 탈퇴가 오면 탈퇴가 로그인의 커밋을 기다렸다가 방금 발급한 refresh까지 폐기한다.
     * 어차피 새 refresh INSERT의 FK 확인으로 걸릴 공유 잠금을 앞당긴 것이라 잠금이 늘지 않는다.
     * findGuest 다음에 부른다. 두 회원을 잠그는 순서는 게스트 → 기존 회원이다. (ADR 0028)
     */
    private Optional<Member> findRegistered(AuthProvider provider, String providerId) {
        return memberRepository.findIdByProviderAndProviderId(provider, providerId)
                .flatMap(memberRepository::findByIdForShare)
                .filter(Member::isActive);
    }

    private Optional<Member> findGuest(Long memberId) {
        if (memberId == null) {
            return Optional.empty();
        }
        return memberRepository.findByIdForUpdate(memberId)
                .filter(Member::isGuest)
                .filter(Member::isActive);
    }
}
