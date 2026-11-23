package com.mapmory.backend.tag;

import com.mapmory.backend.member.api.MemberDataPurger;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/**
 * 회원의 태그를 지운다. 기록과의 연결은 기록을 지울 때 함께 지워졌다.
 */
@Component
@Order(3)
class TagPurger implements MemberDataPurger {

    private final TagRepository tagRepository;

    TagPurger(TagRepository tagRepository) {
        this.tagRepository = tagRepository;
    }

    @Override
    public void purge(Long memberId) {
        tagRepository.deleteAllByMemberId(memberId);
    }
}
