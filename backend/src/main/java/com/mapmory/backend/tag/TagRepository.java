package com.mapmory.backend.tag;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

public interface TagRepository extends JpaRepository<Tag, Long> {
    long countByMemberId(Long memberId);

    @Query("""
            SELECT CASE WHEN COUNT(t) > 0 THEN TRUE ELSE FALSE END
            FROM Tag t
            WHERE t.member.id = :memberId
              AND t.tagName.nameKey = :nameKey
            """)
    boolean existsByMemberIdAndNameKey(
            @Param("memberId") Long memberId,
            @Param("nameKey") String nameKey
    );

    @Query("""
            SELECT CASE WHEN COUNT(t) > 0 THEN TRUE ELSE FALSE END
            FROM Tag t
            WHERE t.member.id = :memberId
              AND t.tagName.nameKey = :nameKey
              AND t.id <> :id
            """)
    boolean existsByMemberIdAndNameKeyAndIdNot(
            @Param("memberId") Long memberId,
            @Param("nameKey") String nameKey,
            @Param("id") Long id
    );

    Optional<Tag> findByIdAndMemberId(Long id, Long memberId);

    List<Tag> findAllByMemberIdOrderByCreatedAtAscIdAsc(Long memberId);

    List<Tag> findAllByMemberIdAndIdInOrderByCreatedAtAscIdAsc(Long memberId, Collection<Long> ids);

    // 회원 데이터 파기.
    @Modifying
    @Transactional
    @Query("DELETE FROM Tag t WHERE t.member.id = :memberId")
    void deleteAllByMemberId(@Param("memberId") Long memberId);
}
