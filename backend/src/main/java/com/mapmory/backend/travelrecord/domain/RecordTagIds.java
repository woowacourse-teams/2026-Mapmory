package com.mapmory.backend.travelrecord.domain;

import com.mapmory.backend.common.exception.BusinessException;
import com.mapmory.backend.tag.TagErrorCode;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;

/**
 * 한 일지에 붙일 태그 id. 최대 {@value #MAX_TAGS}개이며 중복될 수 없다.
 *
 * <p>요청을 받자마자 만들어 외부 호출(S3, Google) 전에 거절한다.
 * 태그가 실제로 있고 그 회원의 것인지는 {@link TravelRecord#retag}가 조회한 태그로 판단한다.
 */
public final class RecordTagIds {
    private static final int MAX_TAGS = 5;

    private final List<Long> values;

    private RecordTagIds(List<Long> values) {
        this.values = values;
    }

    public static RecordTagIds from(List<Long> rawIds) {
        List<Long> ids = rawIds == null ? List.of() : rawIds;
        if (ids.size() > MAX_TAGS) {
            throw new BusinessException(TagErrorCode.TOO_MANY_TAGS);
        }
        // List.of로 만든 목록은 contains(null)에서 예외를 던지므로 null을 따로 찾는다.
        if (ids.stream().anyMatch(Objects::isNull) || new HashSet<>(ids).size() != ids.size()) {
            throw new BusinessException(TagErrorCode.INVALID_TAG_IDS);
        }
        return new RecordTagIds(List.copyOf(ids));
    }

    public List<Long> values() {
        return values;
    }

    public boolean isEmpty() {
        return values.isEmpty();
    }

    boolean contains(Long tagId) {
        return values.contains(tagId);
    }
}
