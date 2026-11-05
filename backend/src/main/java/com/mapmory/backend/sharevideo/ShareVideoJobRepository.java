package com.mapmory.backend.sharevideo;

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

public interface ShareVideoJobRepository extends JpaRepository<ShareVideoJob, Long> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT job FROM ShareVideoJob job WHERE job.id = :jobId")
    Optional<ShareVideoJob> findByIdForUpdate(@Param("jobId") Long jobId);

    @Query("SELECT job FROM ShareVideoJob job WHERE job.id = :jobId AND job.member.id = :memberId")
    Optional<ShareVideoJob> findByIdAndMemberId(
            @Param("jobId") Long jobId,
            @Param("memberId") Long memberId
    );

    @Query("SELECT job.resultObjectKey FROM ShareVideoJob job WHERE job.member.id = :memberId AND job.resultObjectKey IS NOT NULL")
    List<String> findResultKeysByMemberId(@Param("memberId") Long memberId);

    @Query("SELECT job.attemptObjectKey FROM ShareVideoJob job WHERE job.member.id = :memberId AND job.attemptObjectKey IS NOT NULL")
    List<String> findAttemptKeysByMemberId(@Param("memberId") Long memberId);

    // 대기 중이거나 렌더링 중인 작업이 있는지. 있으면 새 시도본이 올라올 수 있다.
    @Query("""
        SELECT COUNT(job) > 0 FROM ShareVideoJob job
        WHERE job.member.id = :memberId AND job.status IN :statuses
    """)
    boolean existsByMemberIdAndStatusIn(
            @Param("memberId") Long memberId,
            @Param("statuses") Collection<ShareVideoStatus> statuses
    );

    // 회원 데이터 파기. 객체를 지운 뒤에 부른다.
    @Modifying
    @Transactional
    @Query("DELETE FROM ShareVideoJob job WHERE job.member.id = :memberId")
    void deleteAllByMemberId(@Param("memberId") Long memberId);

    @Query(value = """
        SELECT *
        FROM share_video_job
        WHERE status = 'PENDING'
        ORDER BY created_at ASC, id ASC
        LIMIT 1
        FOR UPDATE SKIP LOCKED
        """, nativeQuery = true)
    Optional<ShareVideoJob> findNextPendingForUpdate();
}
