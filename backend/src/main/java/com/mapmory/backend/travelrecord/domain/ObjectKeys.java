package com.mapmory.backend.travelrecord.domain;

import com.mapmory.backend.common.exception.BusinessException;
import com.mapmory.backend.travelrecord.exception.TravelRecordErrorCode;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * 한 일지에 붙일 사진 Object Key 목록. 순서가 곧 사진 순서다.
 *
 * <p>만들어졌다면 모든 키가 형식에 맞고({@link ObjectKey}) 서로 겹치지 않는다.
 * 요청을 받자마자 만들어 외부 호출(S3) 전에 거절한다.
 * 그 회원에게 발급했는지, S3에 있는지, 다른 일지가 쓰는지는 I/O가 필요해 응용 서비스가 따로 확인한다.
 */
public final class ObjectKeys {

    private final List<ObjectKey> keys;

    private ObjectKeys(List<ObjectKey> keys) {
        this.keys = keys;
    }

    public static ObjectKeys from(List<String> rawKeys) {
        List<String> raw = rawKeys == null ? List.of() : rawKeys;
        List<ObjectKey> keys = raw.stream()
                .map(ObjectKey::from)
                .toList();
        if (new HashSet<>(keys).size() != keys.size()) {
            throw new BusinessException(TravelRecordErrorCode.INVALID_OBJECT_KEY);
        }
        return new ObjectKeys(keys);
    }

    /**
     * 이 목록 중 existing에 없는 키. 순서를 지킨다.
     */
    ObjectKeys excluding(Set<ObjectKey> existing) {
        return new ObjectKeys(keys.stream()
                .filter(key -> !existing.contains(key))
                .toList());
    }

    /**
     * 업로드 모듈과 리포지토리에 넘길 문자열. 업로드 모듈이 여행 기록을 모르도록 값만 꺼낸다.
     */
    public List<String> values() {
        return keys.stream()
                .map(ObjectKey::value)
                .toList();
    }

    public boolean isEmpty() {
        return keys.isEmpty();
    }

    List<ObjectKey> keys() {
        return keys;
    }
}
