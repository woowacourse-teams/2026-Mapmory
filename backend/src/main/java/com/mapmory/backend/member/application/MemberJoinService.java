package com.mapmory.backend.member.application;

import com.mapmory.backend.member.AuthProvider;
import com.mapmory.backend.member.Member;
import com.mapmory.backend.member.MemberName;
import com.mapmory.backend.member.MemberRepository;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 로그인할 때 어떤 회원으로 들어오는가를 정한다. 토큰은 다루지 않는다.
 */
@Service
public class MemberJoinService {

    private final MemberRepository memberRepository;

    public MemberJoinService(MemberRepository memberRepository) {
        this.memberRepository = memberRepository;
    }

    /**
     * 소셜 계정으로 들어온다.
     *
     * 게스트로 사용 중이었다면(guestMemberId가 게스트 회원) 새 회원을 만들지 않고
     * 그 회원을 승격해, 게스트로 남긴 기록이 그대로 이어지게 한다.
     *
     * 이미 같은 소셜 계정으로 가입한 회원이 있으면 한 사람에게 회원 행이 둘인 상황이므로,
     * 기존 회원을 우선하고 게스트는 leftGuest로 돌려준다. 이때 게스트가 남긴 기록은 이어지지 않는다.
     * (ADR 0015)
     */
    @Transactional
    public SocialJoin joinWithSocial(AuthProvider provider, String providerId, String providerName, Long guestMemberId) {
        Optional<Member> guest = findGuest(guestMemberId);

        Optional<Member> registered = memberRepository.findByProviderAndProviderId(provider, providerId);
        if (registered.isPresent()) {
            return new SocialJoin(registered.get(), SocialJoinType.EXISTING_ACCOUNT, guest.orElse(null));
        }

        if (guest.isPresent()) {
            Member promoted = guest.get();
            promoted.promote(provider, providerId, providerName);
            return new SocialJoin(promoted, SocialJoinType.PROMOTED, null);
        }

        Member joined = memberRepository.save(Member.ofOAuth(
                provider,
                providerId,
                MemberName.fromProvider(providerName),
                UUID.randomUUID()
        ));
        return new SocialJoin(joined, SocialJoinType.SOCIAL_JOINED, null);
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

    private Optional<Member> findGuest(Long memberId) {
        if (memberId == null) {
            return Optional.empty();
        }
        return memberRepository.findById(memberId)
                .filter(member -> member.getProvider() == AuthProvider.GUEST);
    }
}
