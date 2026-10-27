package com.mapmory.backend.group.domain;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface AlbumRepository extends JpaRepository<Album, Long> {

    // 앨범 화면은 항목과 지역을 모두 쓴다. 지역은 즉시 로딩이라 함께 읽지 않으면 조회가 한 번 더 나간다.
    // 그룹 경로로만 찾는다. 다른 그룹의 앨범이면 없는 것과 같다.
    @Query("""
        SELECT a FROM Album a
        JOIN FETCH a.region
        LEFT JOIN FETCH a.entries
        WHERE a.id = :albumId AND a.groupId = :groupId
    """)
    Optional<Album> findByIdAndGroupId(@Param("albumId") Long albumId, @Param("groupId") Long groupId);
}
