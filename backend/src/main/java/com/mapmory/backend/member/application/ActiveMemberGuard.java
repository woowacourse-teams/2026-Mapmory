package com.mapmory.backend.member.application;

import com.mapmory.backend.common.exception.BusinessException;
import com.mapmory.backend.member.Member;
import com.mapmory.backend.member.MemberRepository;
import com.mapmory.backend.member.exception.MemberErrorCode;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * 데이터를 만들려는 회원이 활동 중인지 확인한다. 없거나 탈퇴했거나 버려진 회원은 INACTIVE_MEMBER(401)다.
 *
 * <p>access token은 탈퇴해도 만료(최대 30분)까지 유효하다. JWT 필터는 요청마다 회원을 조회하지 않으므로(ADR 0009),
 * 남은 토큰으로 데이터나 비용을 만드는 경로가 서비스 트랜잭션에서 이 가드를 부른다(ADR 0021).
 * {@code @LoginMember}로 받은 회원은 id만 가진 프록시라 여기서 다시 읽는다.
 */
@Component
public class ActiveMemberGuard {

    private final MemberRepository memberRepository;

    public ActiveMemberGuard(MemberRepository memberRepository) {
        this.memberRepository = memberRepository;
    }

    @Transactional(readOnly = true)
    public Member require(Long memberId) {
        return memberRepository.findById(memberId)
                .filter(Member::isActive)
                .orElseThrow(() -> new BusinessException(MemberErrorCode.INACTIVE_MEMBER));
    }

    /**
     * {@link #require}와 같지만 회원 행을 잠그고(FOR UPDATE) 확인한다. 회원 한 명에 매인 상한(예: 회원당 그룹 수)을
     * 세고 넣는 흐름이 같은 회원의 요청을 차례로 처리하려고 쓴다. 잠금은 부르는 쪽 트랜잭션이 끝날 때 풀린다.
     *
     * <p>REPEATABLE READ의 스냅샷은 트랜잭션의 첫 일반 조회 때 생긴다. 이 잠금이 그 트랜잭션의 첫 DB 조회여야
     * 앞 요청이 커밋한 행을 뒤 요청이 센다. 잠금 없는 트랜잭션에서 부르면 아무것도 막지 못하므로 트랜잭션 안에서만 부른다.
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public Member lock(Long memberId) {
        return memberRepository.findByIdForUpdate(memberId)
                .filter(Member::isActive)
                .orElseThrow(() -> new BusinessException(MemberErrorCode.INACTIVE_MEMBER));
    }
}
