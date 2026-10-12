package com.mapmory.backend.auth.security.resolver;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.mapmory.backend.auth.exception.AuthErrorCode;
import com.mapmory.backend.auth.security.LoginMember;
import com.mapmory.backend.common.exception.BusinessException;
import com.mapmory.backend.member.Member;
import com.mapmory.backend.member.MemberRepository;
import com.mapmory.backend.member.exception.MemberErrorCode;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.MethodParameter;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

@ExtendWith(MockitoExtension.class)
class LoginMemberArgumentResolverTest {

    @Mock
    private MemberRepository memberRepository;

    @Mock
    private MethodParameter methodParameter;

    @InjectMocks
    private LoginMemberArgumentResolver resolver;

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void LoginMember가_붙은_Member_파라미터를_지원한다() {
        when(methodParameter.hasParameterAnnotation(LoginMember.class)).thenReturn(true);
        doReturn(Member.class).when(methodParameter).getParameterType();

        assertThat(resolver.supportsParameter(methodParameter)).isTrue();
    }

    @Test
    void SecurityContext의_memberId로_Member를_조회한다() {
        Member member = Member.of("테스터", java.util.UUID.randomUUID());
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(10L, null, List.of())
        );
        when(memberRepository.findById(10L)).thenReturn(Optional.of(member));

        Object resolved = resolver.resolveArgument(null, null, null, null);

        assertThat(resolved).isSameAs(member);
    }

    @Test
    void 인증_정보가_올바르지_않으면_인증_오류를_반환한다() {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken("invalid", null, List.of())
        );

        assertThatThrownBy(() -> resolver.resolveArgument(null, null, null, null))
                .isInstanceOfSatisfying(BusinessException.class, exception ->
                        assertThat(exception.getErrorCode()).isEqualTo(AuthErrorCode.INVALID_ACCESS_TOKEN));
        verifyNoInteractions(memberRepository);
    }

    @Test
    void 인증된_회원을_찾을_수_없으면_MEMBER_NOT_FOUND를_반환한다() {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(999L, null, List.of())
        );
        when(memberRepository.findById(999L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> resolver.resolveArgument(null, null, null, null))
                .isInstanceOfSatisfying(BusinessException.class, exception ->
                        assertThat(exception.getErrorCode()).isEqualTo(MemberErrorCode.MEMBER_NOT_FOUND));
    }
}
