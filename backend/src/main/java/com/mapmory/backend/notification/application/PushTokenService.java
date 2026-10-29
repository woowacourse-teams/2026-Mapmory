package com.mapmory.backend.notification.application;

import com.mapmory.backend.member.application.ActiveMemberGuard;
import com.mapmory.backend.notification.domain.PushPlatform;
import com.mapmory.backend.notification.domain.PushToken;
import com.mapmory.backend.notification.domain.PushTokenRepository;
import java.util.Collection;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 기기 토큰 등록과 삭제. 앱은 로그인 뒤와 FCM이 토큰을 새로 줄 때 등록하고, 로그아웃 전에 지운다.
 */
@Service
public class PushTokenService {

    private final PushTokenRepository pushTokenRepository;
    private final ActiveMemberGuard activeMemberGuard;

    public PushTokenService(PushTokenRepository pushTokenRepository, ActiveMemberGuard activeMemberGuard) {
        this.pushTokenRepository = pushTokenRepository;
        this.activeMemberGuard = activeMemberGuard;
    }

    /**
     * 같은 토큰이 이미 있으면 이 회원으로 옮긴다(기기에서 계정을 바꿈). 탈퇴 뒤 남은 access token으로는 등록하지 못한다(#523).
     */
    @Transactional
    public void register(Long memberId, String token, PushPlatform platform) {
        activeMemberGuard.require(memberId);
        pushTokenRepository.findByToken(token).ifPresentOrElse(
                existing -> existing.assignTo(memberId, platform),
                () -> pushTokenRepository.save(new PushToken(memberId, token, platform)));
    }

    /**
     * FCM이 더 이상 유효하지 않다고 답한 토큰을 지운다. 앱을 지운 기기 등이다.
     */
    @Transactional
    public void removeInvalid(Collection<String> tokens) {
        if (!tokens.isEmpty()) {
            pushTokenRepository.deleteAllByTokenIn(tokens);
        }
    }

    /**
     * 내 토큰만 지운다. 없거나 남의 토큰이면 아무것도 하지 않는다.
     */
    @Transactional
    public void unregister(Long memberId, String token) {
        pushTokenRepository.deleteByTokenAndMemberId(token, memberId);
    }
}
