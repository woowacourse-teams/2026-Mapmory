package com.mapmory.backend.servicefeedback;

import com.mapmory.backend.member.api.MemberDataPurger;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/**
 * 회원의 의견을 익명화한다. 내용은 서비스 개선에 쓰므로 지우지 않고 작성자 연결만 끊는다.
 */
@Component
@Order(4)
class ServiceFeedbackPurger implements MemberDataPurger {

    private final ServiceFeedbackRepository serviceFeedbackRepository;

    ServiceFeedbackPurger(ServiceFeedbackRepository serviceFeedbackRepository) {
        this.serviceFeedbackRepository = serviceFeedbackRepository;
    }

    @Override
    public void purge(Long memberId) {
        serviceFeedbackRepository.anonymizeByMemberId(memberId);
    }
}
