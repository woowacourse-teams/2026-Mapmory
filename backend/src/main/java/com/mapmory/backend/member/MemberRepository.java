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

    // 회원을 바꾸고 refresh를 폐기하는 흐름(승격·버림·탈퇴)의 행 잠금(SELECT ... FOR UPDATE). 같은 회원의 요청을 직렬화하고,
    // 잠그는 순서를 회원 → refresh로 맞춘다. (#574, ADR 0028)
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select m from Member m where m.id = :id")
    Optional<Member> findByIdForUpdate(@Param("id") Long id);

    // refresh 회전 시 공유 잠금(SELECT ... FOR SHARE). 승격·탈퇴처럼 회원을 먼저 잠그고 refresh를 폐기하는 흐름과
    // 잠그는 순서를 회원 → refresh로 맞춰 데드락을 막는다. 회전끼리는 서로 막지 않는다. (#579, ADR 0028)
    @Lock(LockModeType.PESSIMISTIC_READ)
    @Query("select m from Member m where m.id = :id")
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

    boolean existsByProviderAndProviderId(AuthProvider provider, String providerId);
}
