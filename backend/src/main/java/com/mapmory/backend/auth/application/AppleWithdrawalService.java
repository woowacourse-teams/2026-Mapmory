package com.mapmory.backend.auth.application;

import com.mapmory.backend.auth.application.model.AppleReauthentication;
import com.mapmory.backend.auth.application.port.AppleRevocationPort;
import com.mapmory.backend.auth.exception.AuthErrorCode;
import com.mapmory.backend.common.exception.BusinessException;
import com.mapmory.backend.member.AuthProvider;
import com.mapmory.backend.member.Member;
import com.mapmory.backend.member.application.MemberService;
import org.springframework.stereotype.Service;

/**
 * 애플 회원 탈퇴. 애플 토큰을 철회한 뒤 탈퇴한다. (앱스토어 심사 지침 5.1.1(v), ADR 0025)
 *
 * 서버는 애플 토큰을 저장하지 않으므로, 앱이 탈퇴할 때 애플 로그인을 다시 해 identity token과
 * authorization code(5분, 한 번만 쓸 수 있음)를 보낸다.
 *
 * 재인증한 애플 계정이 탈퇴하는 회원 본인이어야 한다(sub == provider_id). 확인하지 않으면 다른 애플 계정으로
 * 재인증해도 탈퇴된다.
 *
 * 철회가 실패하면 탈퇴하지 않는다. 토큰을 저장하지 않아 나중에 다시 철회할 수 없고, 사용자가 앞에 있어
 * 다시 시도하게 할 수 있다. 외부 호출을 먼저 하고 탈퇴는 그 뒤 자기 트랜잭션에서 한다(이 메서드에는 트랜잭션이 없다).
 * 철회 뒤 탈퇴가 실패하면 애플 연결만 끊긴다. 다시 애플로 로그인하면 sub가 같아 같은 회원으로 들어온다.
 */
@Service
public class AppleWithdrawalService {

    private final MemberService memberService;
    private final AppleRevocationPort appleRevocationPort;

    public AppleWithdrawalService(MemberService memberService, AppleRevocationPort appleRevocationPort) {
        this.memberService = memberService;
        this.appleRevocationPort = appleRevocationPort;
    }

    public void withdraw(Long memberId, String identityToken, String authorizationCode) {
        Member member = memberService.findMe(memberId);
        if (member.getProvider() != AuthProvider.APPLE) {
            throw new BusinessException(AuthErrorCode.NOT_APPLE_MEMBER);
        }

        AppleReauthentication reauthentication = appleRevocationPort.verify(identityToken);
        if (!reauthentication.subject().equals(member.getProviderId())) {
            throw new BusinessException(AuthErrorCode.APPLE_REAUTHENTICATION_FAILED);
        }

        appleRevocationPort.revoke(reauthentication, authorizationCode);
        memberService.withdrawUnlinked(memberId);
    }
}
