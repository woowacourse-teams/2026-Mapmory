package com.mapmory.backend.waitlist;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface LaunchWaitlistRepository extends JpaRepository<LaunchWaitlistEntry, Long> {

    @Query("""
            SELECT CASE WHEN COUNT(e) > 0 THEN TRUE ELSE FALSE END
            FROM LaunchWaitlistEntry e
            WHERE e.email.value = :email
            """)
    boolean existsByEmail(@Param("email") String email);
}
