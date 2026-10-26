package com.mapmory.backend.group.domain;

import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface TravelGroupRepository extends JpaRepository<TravelGroup, Long> {

    @Query("""
        SELECT COUNT(gm) > 0 FROM GroupMember gm
        WHERE gm.group.id = :groupId AND gm.memberId = :memberId
    """)
    boolean existsMember(@Param("groupId") Long groupId, @Param("memberId") Long memberId);

    // 목록 화면 하나를 그리려고 그룹을 다 불러오지 않게 개수만 센다. 앨범 수는 읽기 전용으로 album을 함께 센다.
    @Query("""
        SELECT new com.mapmory.backend.group.domain.GroupSummary(
            g.id,
            g.name,
            (SELECT COUNT(other) FROM GroupMember other WHERE other.group = g),
            (SELECT COUNT(a) FROM Album a WHERE a.groupId = g.id)
        )
        FROM GroupMember me JOIN me.group g
        WHERE me.memberId = :memberId
        ORDER BY me.joinedAt ASC, me.id ASC
    """)
    List<GroupSummary> findSummariesByMemberId(@Param("memberId") Long memberId);

    // 초대 수락이 동시에 들어와도 30명을 넘거나 같은 사람이 두 번 들어가지 않게 그룹 행을 잠근다.
    // 멤버는 잠근 뒤에 따로 읽으므로, 부르는 쪽 트랜잭션은 READ COMMITTED여야 먼저 들어온 멤버가 보인다.
    // (멤버를 fetch join해도 Hibernate가 잠금을 별도 쿼리로 나눠서 소용없다.)
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT g FROM TravelGroup g WHERE g.id = :groupId")
    Optional<TravelGroup> findByIdForUpdate(@Param("groupId") Long groupId);
}
