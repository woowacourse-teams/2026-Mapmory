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

    @Query("SELECT COUNT(gm) FROM GroupMember gm WHERE gm.memberId = :memberId")
    long countGroupsOf(@Param("memberId") Long memberId);

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

    // 초대 수락이 동시에 들어와도 30명을 넘지 않게 그룹 행을 잠근다. 잠금으로 읽은 행(memberCount 포함)은
    // 기본 격리 수준(REPEATABLE READ)에서도 최신이라, 앞 사람이 넣은 인원까지 보고 판단한다.
    // 그룹을 바꾸는 쿼리는 모두 그룹 행을 먼저 잠근다(잠금 순서가 같아야 교착이 없다).
    // 트랜잭션에서 그룹을 처음 읽는 게 이 메서드여야 한다. 먼저 일반 조회로 불러 두면 1차 캐시의 옛 값을 돌려준다.
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT g FROM TravelGroup g WHERE g.id = :groupId")
    Optional<TravelGroup> findByIdForUpdate(@Param("groupId") Long groupId);
}
