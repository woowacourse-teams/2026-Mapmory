package com.mapmory.backend.sharevideo;

import jakarta.persistence.LockModeType;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ShareVideoJobRepository extends JpaRepository<ShareVideoJob, Long> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT job FROM ShareVideoJob job WHERE job.id = :jobId")
    Optional<ShareVideoJob> findByIdForUpdate(@Param("jobId") Long jobId);

    @Query("SELECT job FROM ShareVideoJob job WHERE job.id = :jobId AND job.member.id = :memberId")
    Optional<ShareVideoJob> findByIdAndMemberId(
            @Param("jobId") Long jobId,
            @Param("memberId") Long memberId
    );

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
