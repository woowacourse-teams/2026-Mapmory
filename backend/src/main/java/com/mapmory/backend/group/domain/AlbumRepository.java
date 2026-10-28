package com.mapmory.backend.group.domain;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Limit;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
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

    // 그룹 홈의 앨범 목록. 최근에 기록이 담긴 앨범이 먼저다. 한 페이지의 id만 고르고 앨범은 findAllWithEntriesByIdIn으로 읽는다.
    // 항목과 JOIN하므로 항목이 없는 앨범은 나오지 않는다(빈 앨범은 지운다는 팀 추천과 같다).
    // GROUP BY가 있으면 Spring이 만드는 COUNT 쿼리가 틀리므로 countQuery를 따로 적는다.
    @Query(value = """
        SELECT a.id FROM Album a JOIN a.entries e
        WHERE a.groupId = :groupId
        GROUP BY a.id
        ORDER BY MAX(e.createdAt) DESC, a.id DESC
    """, countQuery = """
        SELECT COUNT(a) FROM Album a
        WHERE a.groupId = :groupId AND EXISTS (SELECT 1 FROM AlbumEntry e WHERE e.album = a)
    """)
    Page<Long> findIdsByGroupIdOrderByLastAdded(@Param("groupId") Long groupId, Pageable pageable);

    // 그룹 카드의 대표 사진용. findIdsByGroupIdOrderByLastAdded와 같은 순서이고 전체 수는 세지 않는다.
    @Query("""
        SELECT a.id FROM Album a JOIN a.entries e
        WHERE a.groupId = :groupId
        GROUP BY a.id
        ORDER BY MAX(e.createdAt) DESC, a.id DESC
    """)
    List<Long> findLatestIdsByGroupId(@Param("groupId") Long groupId, Limit limit);

    // 기록이 지워졌을 때 정리할 그룹들. idx_album_entry_travel_record를 탄다.
    @Query("""
        SELECT DISTINCT a.groupId FROM Album a JOIN a.entries e
        WHERE e.travelRecordId = :travelRecordId
    """)
    List<Long> findGroupIdsContainingRecord(@Param("travelRecordId") Long travelRecordId);

    // 그룹 잠금 뒤에 부른다. 항목을 떼고 비었는지 보려고 항목을 모두 읽는다.
    @Query("""
        SELECT DISTINCT a FROM Album a
        LEFT JOIN FETCH a.entries
        WHERE a.groupId = :groupId
          AND EXISTS (SELECT 1 FROM AlbumEntry e WHERE e.album = a AND e.travelRecordId = :travelRecordId)
    """)
    List<Album> findAllByGroupIdContainingRecord(@Param("groupId") Long groupId,
                                                 @Param("travelRecordId") Long travelRecordId);

    // 그룹을 나갈 때 그 멤버의 항목이 있는 앨범들. 그룹 잠금 뒤에 부른다. 연 사람을 넘기고 비었는지 보려고 항목을 모두 읽는다.
    @Query("""
        SELECT DISTINCT a FROM Album a
        LEFT JOIN FETCH a.entries
        WHERE a.groupId = :groupId
          AND EXISTS (SELECT 1 FROM AlbumEntry e WHERE e.album = a AND e.memberId = :memberId)
    """)
    List<Album> findAllByGroupIdContainingMember(@Param("groupId") Long groupId, @Param("memberId") Long memberId);

    // 그룹을 지울 때 남은 앨범들. 마지막 멤버의 항목을 뗀 뒤라 보통 비어 있다.
    List<Album> findAllByGroupId(Long groupId);

    // 순서는 정하지 않는다. 부르는 쪽이 findIdsByGroupIdOrderByLastAdded의 순서로 맞춘다.
    @Query("""
        SELECT DISTINCT a FROM Album a
        JOIN FETCH a.region
        LEFT JOIN FETCH a.entries
        WHERE a.id IN :ids
    """)
    List<Album> findAllWithEntriesByIdIn(@Param("ids") Collection<Long> ids);
}
