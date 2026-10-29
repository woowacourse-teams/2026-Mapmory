package com.mapmory.backend.auth.application;

import com.mapmory.backend.auth.application.model.AppleReauthentication;
import com.mapmory.backend.auth.application.port.AppleRevocationPort;
import com.mapmory.backend.auth.exception.AuthErrorCode;
import com.mapmory.backend.common.exception.BusinessException;
import com.mapmory.backend.common.logging.RequestIdFilter;
import com.mapmory.backend.member.AuthProvider;
import com.mapmory.backend.member.Member;
import com.mapmory.backend.member.application.MemberService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
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
 * 철회 뒤 탈퇴가 실패하면 APPLE_WITHDRAWAL_INCOMPLETE로 알려 새 재인증을 요청한다.
 * 애플과 DB를 함께 롤백하거나 자동 복구하지는 못한다. 운영 대응은 ADR 0025를 따른다.
 */
@Service
public class AppleWithdrawalService {

    private static final Logger log = LoggerFactory.getLogger(AppleWithdrawalService.class);

    private final MemberService memberService;
    private final AppleRevocationPort appleRevocationPort;

    public AppleWithdrawalService(MemberService memberService, AppleRevocationPort appleRevocationPort) {
        this.memberService = memberService;
        this.appleRevocationPort = appleRevocationPort;
    }

    public void withdraw(Long memberId, String identityToken, String authorizationCode) {
        Member member = memberService.findForWithdrawal(memberId);
        if (member.getProvider() != AuthProvider.APPLE) {
            throw new BusinessException(AuthErrorCode.NOT_APPLE_MEMBER);
        }

        if (member.isWithdrawn()) {
            return;
        }

        AppleReauthentication reauthentication = appleRevocationPort.verify(identityToken);
        if (!reauthentication.subject().equals(member.getProviderId())) {
            throw new BusinessException(AuthErrorCode.APPLE_REAUTHENTICATION_FAILED);
        }

        appleRevocationPort.revoke(reauthentication, authorizationCode);
        try {
            memberService.withdrawUnlinked(memberId);
        } catch (RuntimeException exception) {
            // 애플 철회는 완료됐지만 로컬 탈퇴는 완료되지 않았다. 토큰/코드/예외 메시지는 기록하지 않는다.
            log.atError()
                    .addKeyValue("event", "APPLE_WITHDRAWAL_LOCAL_FAILED")
                    .addKeyValue("memberId", memberId)
                    .addKeyValue("requestId", MDC.get(RequestIdFilter.REQUEST_ID_MDC_KEY))
                    .addKeyValue("stage", "MEMBER_WITHDRAWAL")
                    .addKeyValue("failureType", exception.getClass().getSimpleName())
                    .log("Apple revocation succeeded but member withdrawal failed");
            throw new BusinessException(AuthErrorCode.APPLE_WITHDRAWAL_INCOMPLETE);
        }
    }
}
