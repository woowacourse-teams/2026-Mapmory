package com.mapmory.backend.upload.service.port;

/**
 * 저장소에 올라간 여행 기록 객체를 한 쪽씩 읽는다. 기록에 붙지 않은 객체를 찾는 대조 배치가 쓴다. (#186)
 */
public interface StoredObjectLister {

    /**
     * 여행 기록 경로 아래의 객체를 한 쪽 읽는다. 우리 경로 밖은 읽지 않는다.
     *
     * @param continuationToken 처음에는 null, 다음부터는 앞 쪽의 {@link StoredObjectPage#nextToken()}
     */
    StoredObjectPage listTravelRecordObjects(String continuationToken);
}
