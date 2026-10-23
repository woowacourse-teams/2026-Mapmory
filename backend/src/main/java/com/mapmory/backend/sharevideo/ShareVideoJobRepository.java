package com.mapmory.backend.sharevideo;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface ShareVideoJobRepository extends JpaRepository<ShareVideoJob, Long> {
    Optional<ShareVideoJob> findByIdAndMemberId(
            Long jobId,
            Long memberId
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
