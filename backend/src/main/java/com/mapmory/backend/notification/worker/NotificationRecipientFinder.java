package com.mapmory.backend.notification.worker;

import com.mapmory.backend.member.Member;
import com.mapmory.backend.member.MemberRepository;
import com.mapmory.backend.notification.domain.PushToken;
import com.mapmory.backend.notification.domain.PushTokenRepository;
import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class NotificationRecipientFinder {

    private final MemberRepository memberRepository;
    private final PushTokenRepository pushTokenRepository;

    public NotificationRecipientFinder(
            final MemberRepository memberRepository,
            final PushTokenRepository pushTokenRepository
    ) {
        this.memberRepository = memberRepository;
        this.pushTokenRepository = pushTokenRepository;
    }

    @Transactional(readOnly = true)
    public Optional<PushToken> find(final ClaimedNotification notification) {
        boolean activeMember = memberRepository.findById(notification.memberId())
                .filter(Member::isActive)
                .isPresent();

        if (!activeMember) {
            return Optional.empty();
        }

        return pushTokenRepository.findByIdAndMemberId(
                notification.pushTokenId(),
                notification.memberId()
        );
    }
}
