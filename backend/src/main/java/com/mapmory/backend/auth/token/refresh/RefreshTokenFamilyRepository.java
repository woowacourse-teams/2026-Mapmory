package com.mapmory.backend.auth.token.refresh;

import jakarta.persistence.LockModeType;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

public interface RefreshTokenFamilyRepository extends JpaRepository<RefreshTokenFamily, Long> {

    // 회전·로그아웃이 세션의 토큰을 잠그기 전에 세션 행을 잠근다(SELECT ... FOR UPDATE).
    // 같은 세션의 회전·재사용 판정·로그아웃이 직렬화되어, 세션 토큰을 서로 다른 순서로 잠그는 데드락이 없다. (#467, ADR 0028)
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select f from RefreshTokenFamily f where f.id = :id")
    Optional<RefreshTokenFamily> findByIdForUpdate(@Param("id") Long id);

    // 토큰이 남지 않은 세션을 id 순서로 나눠 읽는다. (#454)
    @Query(value = """
            SELECT f.id FROM refresh_token_family f
            WHERE f.id > :after
              AND NOT EXISTS (SELECT 1 FROM refresh_token t WHERE t.family_id = f.id)
            ORDER BY f.id
            LIMIT :limit
            """, nativeQuery = true)
    List<Long> findEmptyIdsAfter(@Param("after") long after, @Param("limit") int limit);

    @Modifying
    @Transactional
    @Query("delete from RefreshTokenFamily f where f.id in :ids")
    void deleteAllByIdIn(@Param("ids") Collection<Long> ids);
}
