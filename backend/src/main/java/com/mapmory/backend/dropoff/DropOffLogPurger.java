package com.mapmory.backend.dropoff;

import com.mapmory.backend.member.api.MemberDataPurger;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/**
 * 파기하는 회원의 막힌 지점 기록을 지운다. 30일 보관이라 대부분 이미 지워졌지만, 탈퇴 뒤 남은 토큰으로 생긴 행까지 지운다.
 */
@Component
@Order(6)
class DropOffLogPurger implements MemberDataPurger {

    private final DropOffLogRepository repository;

    DropOffLogPurger(DropOffLogRepository repository) {
        this.repository = repository;
    }

    @Override
    public void purge(Long memberId) {
        repository.deleteAllByMemberId(memberId);
    }
}
