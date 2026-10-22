package com.mapmory.backend.group.domain;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface TravelGroupRepository extends JpaRepository<TravelGroup, Long> {

    @Query("""
        SELECT COUNT(gm) > 0 FROM GroupMember gm
        WHERE gm.group.id = :groupId AND gm.memberId = :memberId
    """)
    boolean existsMember(@Param("groupId") Long groupId, @Param("memberId") Long memberId);
}
