package com.mapmory.backend.member;

import jakarta.persistence.LockModeType;
import java.time.LocalDateTime;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface MemberRepository extends JpaRepository<Member, Long> {

    Optional<Member> findByProviderAndProviderId(AuthProvider provider, String providerId);

    // 회원 행을 공유 잠금(FOR SHARE)으로 읽는다. 탈퇴(회원 UPDATE)는 이 트랜잭션이 끝날 때까지 기다리고,
    // 이미 시작된 탈퇴가 있으면 그 커밋을 기다렸다가 최신 행을 읽는다. 공유 잠금끼리는 서로 막지 않는다.
    @Lock(LockModeType.PESSIMISTIC_READ)
    @Query("SELECT m FROM Member m WHERE m.id = :id")
    Optional<Member> findByIdForShare(@Param("id") Long id);

    @Query("""
        SELECT m FROM Member m
        WHERE (:provider IS NULL OR m.provider = :provider)
          AND (
              :keyword IS NULL
              OR LOWER(m.memberName.name) LIKE LOWER(CONCAT('%', :keyword, '%'))
              OR (:memberId IS NOT NULL AND m.id = :memberId)
          )
    """)
    Page<Member> searchForAdmin(
            @Param("provider") AuthProvider provider,
            @Param("keyword") String keyword,
            @Param("memberId") Long memberId,
            Pageable pageable
    );

    long countByProvider(AuthProvider provider);

    @Query("SELECT COUNT(m) FROM Member m WHERE m.createdAt >= :from AND m.createdAt < :to")
    long countCreatedBetween(@Param("from") LocalDateTime from, @Param("to") LocalDateTime to);
}
