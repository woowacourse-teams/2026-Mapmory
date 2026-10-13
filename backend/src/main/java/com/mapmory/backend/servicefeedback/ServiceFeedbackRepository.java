package com.mapmory.backend.servicefeedback;

import java.time.LocalDateTime;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ServiceFeedbackRepository extends JpaRepository<ServiceFeedback, Long> {

    @Query("""
        SELECT sf FROM ServiceFeedback sf
        WHERE (:status IS NULL OR sf.status = :status)
          AND (:category IS NULL OR sf.category = :category)
          AND (:platform IS NULL OR sf.platform = :platform)
          AND (:from IS NULL OR sf.createdAt >= :from)
          AND (:toExclusive IS NULL OR sf.createdAt < :toExclusive)
    """)
    Page<ServiceFeedback> searchForAdmin(
            @Param("status") ServiceFeedbackStatus status,
            @Param("category") ServiceFeedbackCategory category,
            @Param("platform") ServiceFeedbackPlatform platform,
            @Param("from") LocalDateTime from,
            @Param("toExclusive") LocalDateTime toExclusive,
            Pageable pageable
    );
}
