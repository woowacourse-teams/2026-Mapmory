package com.mapmory.backend.group.application;

import com.mapmory.backend.common.exception.BusinessException;
import com.mapmory.backend.group.exception.GroupErrorCode;
import com.mapmory.backend.member.AuthProvider;
import com.mapmory.backend.member.Member;
import com.mapmory.backend.member.MemberRepository;
import com.mapmory.backend.member.exception.MemberErrorCode;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * 그룹을 쓰려는 회원이 활동 중인 소셜 로그인 회원인지 확인한다. 그룹 서비스가 맨 먼저 부른다.
 *
 * <p>게스트(와 제공자가 없는 옛 임시 회원)는 기기를 바꾸면 계정을 잃으므로 403이다(ADR 0022 결정 6).
 * 없거나 탈퇴한 회원은 회원 API와 같이 INACTIVE_MEMBER(401)다. 탈퇴 뒤 남은 access token(최대 30분)으로 그룹을 만들지 못하게 한다.
 * @LoginMember로 받은 회원은 id만 가진 프록시라 회원은 부르는 쪽 트랜잭션 안에서 다시 읽는다.
 */
@Component
public class SocialMemberGuard {

    private final MemberRepository memberRepository;

    public SocialMemberGuard(MemberRepository memberRepository) {
        this.memberRepository = memberRepository;
    }

    @Transactional(readOnly = true)
    public void require(Long memberId) {
        Member member = memberRepository.findById(memberId)
                .filter(Member::isActive)
                .orElseThrow(() -> new BusinessException(MemberErrorCode.INACTIVE_MEMBER));
        if (member.getProvider() == null || member.getProvider() == AuthProvider.GUEST) {
            throw new BusinessException(GroupErrorCode.SOCIAL_LOGIN_REQUIRED);
        }
    }
}
