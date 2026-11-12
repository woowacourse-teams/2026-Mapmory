package com.mapmory.backend.dropoff;

import java.time.LocalDateTime;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

public interface DropOffLogRepository extends JpaRepository<DropOffLog, Long> {

    // 오래된 행을 작은 묶음으로 지운다. 한 번에 지우면 잠금이 길어진다.
    @Modifying
    @Transactional
    @Query(value = "DELETE FROM drop_off_log WHERE created_at < :before ORDER BY created_at LIMIT :limit", nativeQuery = true)
    int deleteOlderThan(@Param("before") LocalDateTime before, @Param("limit") int limit);

    @Modifying
    @Transactional
    @Query("DELETE FROM DropOffLog l WHERE l.memberId = :memberId")
    void deleteAllByMemberId(@Param("memberId") Long memberId);
}
