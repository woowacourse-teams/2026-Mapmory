package com.mapmory.backend.sharevideo;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ShareVideoJobRepository extends JpaRepository<ShareVideoJob, Long> {
    Optional<ShareVideoJob> findByIdAndMemberId(
            Long jobId,
            Long memberId
    );
}
