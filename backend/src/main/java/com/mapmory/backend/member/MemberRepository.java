package com.mapmory.backend.member;


import java.time.LocalDateTime;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface MemberRepository extends JpaRepository<Member, Long> {

    Optional<Member> findByProviderAndProviderId(AuthProvider provider, String providerId);

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
