package com.mapmory.backend.member.application;

import com.mapmory.backend.common.exception.BusinessException;
import com.mapmory.backend.member.Member;
import com.mapmory.backend.member.MemberRepository;
import com.mapmory.backend.member.exception.MemberErrorCode;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 로그인한 회원 자신의 정보를 다룬다.
 *
 * access token은 회원 존재를 확인하지 않으므로(ADR 0009) 트랜잭션 안에서 회원을 조회하고,
 * 없으면 MEMBER_NOT_FOUND로 응답한다.
 */
@Service
public class MemberService {

    private final MemberRepository memberRepository;

    public MemberService(MemberRepository memberRepository) {
        this.memberRepository = memberRepository;
    }

    @Transactional(readOnly = true)
    public Member findMe(Long memberId) {
        return findMember(memberId);
    }

    @Transactional
    public Member changeNickname(Long memberId, String nickname) {
        Member member = findMember(memberId);
        member.changeNickname(nickname);
        return member;
    }

    private Member findMember(Long memberId) {
        return memberRepository.findById(memberId)
                .orElseThrow(() -> new BusinessException(MemberErrorCode.MEMBER_NOT_FOUND));
    }
}
