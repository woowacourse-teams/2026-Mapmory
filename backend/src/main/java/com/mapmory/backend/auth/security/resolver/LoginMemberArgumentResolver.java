package com.mapmory.backend.auth.security.resolver;

import com.mapmory.backend.auth.exception.AuthErrorCode;
import com.mapmory.backend.auth.security.LoginMember;
import com.mapmory.backend.common.exception.BusinessException;
import com.mapmory.backend.member.Member;
import jakarta.persistence.EntityManager;
import org.springframework.core.MethodParameter;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.bind.support.WebDataBinderFactory;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.method.support.ModelAndViewContainer;

/**
 * {@link LoginMember}가 붙은 Member 파라미터에 인증된 회원을 주입한다.
 *
 * 인증 정보가 없거나 형식이 예상과 다르면 인증 오류로 처리한다.
 * (보호된 엔드포인트라면 시큐리티 단계에서 이미 걸러지지만, 방어적으로 확인한다.)
 *
 * 회원을 조회하지 않고 id만 가진 프록시를 주입한다. open-in-view가 꺼져 있어 여기서 조회하면
 * 서비스 트랜잭션과 별개로 커넥션을 한 번 더 빌리기 때문이다.
 * {@code MemberRepository.getReferenceById}도 리포지토리 기본 트랜잭션을 열면서 커넥션을 빌리므로
 * 트랜잭션 없이 {@link EntityManager#getReference}를 쓴다.
 * 프록시의 id 외 필드는 트랜잭션 밖에서 읽으면 예외가 나므로, 회원 정보가 필요하면 서비스 트랜잭션 안에서 읽는다.
 * 회원의 존재도 확인하지 않아, 회원 행이 없는 토큰은 조회에서 빈 결과를, 저장에서 외래 키 위반(500)을 낸다.
 * 회원 삭제 기능을 만들 때 함께 다룬다(ADR 0009 2026-10-14 구현 업데이트).
 */
@Component
public class LoginMemberArgumentResolver implements HandlerMethodArgumentResolver {

    private final EntityManager entityManager;

    public LoginMemberArgumentResolver(EntityManager entityManager) {
        this.entityManager = entityManager;
    }

    @Override
    public boolean supportsParameter(MethodParameter parameter) {
        return parameter.hasParameterAnnotation(LoginMember.class)
                && parameter.getParameterType().equals(Member.class);
    }

    @Override
    public Object resolveArgument(
            MethodParameter parameter,
            ModelAndViewContainer mavContainer,
            NativeWebRequest webRequest,
            WebDataBinderFactory binderFactory
    ) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !(authentication.getPrincipal() instanceof Long memberId)) {
            throw new BusinessException(AuthErrorCode.INVALID_ACCESS_TOKEN);
        }
        return entityManager.getReference(Member.class, memberId);
    }
}
