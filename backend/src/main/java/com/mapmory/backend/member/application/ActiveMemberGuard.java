package com.mapmory.backend.member.application;

import com.mapmory.backend.common.exception.BusinessException;
import com.mapmory.backend.member.Member;
import com.mapmory.backend.member.MemberRepository;
import com.mapmory.backend.member.exception.MemberErrorCode;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * 데이터를 만들려는 회원이 활동 중인지 확인한다. 없거나 탈퇴했거나 버려진 회원은 INACTIVE_MEMBER(401)다.
 *
 * <p>access token은 탈퇴해도 만료(최대 30분)까지 유효하다. JWT 필터는 요청마다 회원을 조회하지 않으므로(ADR 0009),
 * 남은 토큰으로 데이터나 비용을 만드는 경로가 서비스 트랜잭션에서 이 가드를 부른다(ADR 0021).
 * {@code @LoginMember}로 받은 회원은 id만 가진 프록시라 여기서 다시 읽는다.
 *
 * <p>회원 행을 공유 잠금으로 읽어, 확인부터 커밋까지를 탈퇴와 차례로 세운다. 일반 조회로 확인하면 확인과 커밋 사이에
 * 탈퇴와 탈퇴 정리(기기 토큰 삭제, 그룹 나가기)가 끝나, 그 뒤 커밋된 데이터가 정리에서 빠진다.
 * 잠금은 부르는 쪽 트랜잭션이 끝날 때 풀린다. 트랜잭션 밖에서 부르면 확인하는 동안만 잡는다.
 * 회원 행에 배타 잠금을 거는 쪽(탈퇴, 닉네임 변경)은 그룹 행을 잠그지 않으므로 그룹 잠금과 교착이 생기지 않는다.
 */
@Component
public class ActiveMemberGuard {

    private final MemberRepository memberRepository;

    public ActiveMemberGuard(MemberRepository memberRepository) {
        this.memberRepository = memberRepository;
    }

    @Transactional(readOnly = true)
    public Member require(Long memberId) {
        return memberRepository.findByIdForShare(memberId)
                .filter(Member::isActive)
                .orElseThrow(() -> new BusinessException(MemberErrorCode.INACTIVE_MEMBER));
    }
}
