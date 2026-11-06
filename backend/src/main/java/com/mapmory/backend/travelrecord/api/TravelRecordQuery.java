package com.mapmory.backend.travelrecord.api;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

/**
 * 다른 컨텍스트가 여행 기록을 읽는 창구. 읽기만 하고 엔티티 대신 값을 넘긴다(ADR 0017, ADR 0022 결정 5).
 *
 * <p>{@link #findOwned} 말고는 소유자를 확인하지 않는다. 부르는 쪽이 먼저 권한을 확인해야 한다
 * (그룹: 요청한 사람이 멤버이고, 기록이 이 그룹의 앨범에 담겨 있다). 지금은 그룹만 쓴다.
 *
 * <p>여러 기록을 받는 메서드는 없는(지워진) id를 오류 없이 결과에서 뺀다. 결과 순서는 정하지 않으므로 부르는 쪽이 id로 짝짓는다.
 * 쿼리 수는 id 수와 상관없이 일정하다(기록 한 번, 사진 한 번).
 */
public interface TravelRecordQuery {

    /**
     * 내 기록이면 요약을, 없거나 남의 기록이면 빈 값을 준다. 앨범 열기와 담기에서 쓴다.
     */
    Optional<RecordBrief> findOwned(Long travelRecordId, Long memberId);

    /**
     * 앨범 목록 카드용 요약.
     */
    List<RecordBrief> briefsOf(Collection<Long> travelRecordIds);

    /**
     * 앨범 상세용. 사진 보기 주소까지 서명한다.
     */
    List<RecordView> viewsOf(Collection<Long> travelRecordIds);
}
