package com.mapmory.backend.servicefeedback;

import java.time.LocalDateTime;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

public interface ServiceFeedbackRepository extends JpaRepository<ServiceFeedback, Long> {

    // 회원 데이터 파기. 의견은 남기고 작성자 연결만 끊는다.
    @Modifying
    @Transactional
    @Query("UPDATE ServiceFeedback f SET f.member = NULL WHERE f.member.id = :memberId")
    void anonymizeByMemberId(@Param("memberId") Long memberId);

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
