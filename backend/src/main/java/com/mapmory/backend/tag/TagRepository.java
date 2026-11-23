package com.mapmory.backend.tag;

import jakarta.persistence.LockModeType;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
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

    /**
     * 기록에 붙일 태그를 공유 잠금(SELECT ... FOR SHARE)으로 읽는다.
     *
     * <p>잠그지 않고 읽으면 스냅샷에는 태그가 있는데 그사이 태그 삭제가 커밋되어, 연결 INSERT가 FK에 걸려 500이 난다.
     * 잠금 조회는 최신 커밋을 본다. 삭제가 먼저면 태그가 없어 TAG_NOT_FOUND로 끝나고, 읽기가 먼저면 삭제가 이 저장의 커밋을 기다린다.
     * 기록 저장은 기록 행 → 태그 행 순서로 잠근다. 연결 INSERT의 FK 확인도 같은 순서로 태그를 공유 잠금하므로 새 순서가 생기지 않는다.
     */
    @Lock(LockModeType.PESSIMISTIC_READ)
    @Query("""
            SELECT t FROM Tag t
            WHERE t.member.id = :memberId AND t.id IN :ids
            ORDER BY t.createdAt ASC, t.id ASC
            """)
    List<Tag> findAllForShareByMemberIdAndIdIn(@Param("memberId") Long memberId, @Param("ids") Collection<Long> ids);

    // 회원 데이터 파기.
    @Modifying
    @Transactional
    @Query("DELETE FROM Tag t WHERE t.member.id = :memberId")
    void deleteAllByMemberId(@Param("memberId") Long memberId);
}
