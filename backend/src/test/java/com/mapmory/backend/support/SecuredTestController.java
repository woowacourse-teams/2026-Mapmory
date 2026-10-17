package com.mapmory.backend.support;

import com.mapmory.backend.auth.security.LoginMember;
import com.mapmory.backend.member.Member;
import org.springframework.boot.test.context.TestComponent;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 인증이 필요한 API를 흉내 내는 테스트용 컨트롤러.
 *
 * SecurityIntegrationTest가 쓰지만 IntegrationTest에서 함께 등록한다.
 * 이 테스트에만 두면 컨텍스트 캐시 키가 달라져 통합 테스트 컨텍스트를 하나 더 띄우게 된다.
 *
 * {@link TestComponent}라 컴포넌트 스캔에서는 빠지고, {@code @Import}한 컨텍스트에만 등록된다.
 */
@TestComponent
@RestController
public class SecuredTestController {

    @GetMapping("/test/secured")
    Long secured(@LoginMember Member member) {
        return member.getId();
    }
}
