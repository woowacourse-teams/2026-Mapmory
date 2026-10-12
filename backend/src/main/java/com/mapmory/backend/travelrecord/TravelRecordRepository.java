package com.mapmory.backend.travelrecord;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface TravelRecordRepository extends JpaRepository<TravelRecord, Long> {

    interface MemberRecordCount {
        Long getMemberId();

        Long getRecordCount();
    }

    interface MemberRecordDetail {
        Long getRecordId();

        LocalDateTime getCreatedAt();

        Long getPhotoCount();
    }

    /**
     * 상세 응답은 지역을 국가·시도·시군구로 펼쳐 보여주므로 Region의 parent·root까지 필요하다.
     * 둘 다 지연 로딩이고 open-in-view가 꺼져 있어, 여기서 함께 읽어두지 않으면
     * 웹 계층의 DTO 변환에서 LazyInitializationException이 난다.
     */
    @EntityGraph(attributePaths = {"region", "region.parent", "region.root"})
    Optional<TravelRecord> findByIdAndMemberId(Long id, Long memberId);

    @Query("SELECT COUNT(tr) FROM TravelRecord tr WHERE tr.createdAt >= :from AND tr.createdAt < :to")
    long countCreatedBetween(@Param("from") LocalDateTime from, @Param("to") LocalDateTime to);

    @Query("SELECT COUNT(rm) FROM RecordMedia rm")
    long countAllMedia();

    @Query("""
        SELECT tr.member.id AS memberId, COUNT(tr.id) AS recordCount
        FROM TravelRecord tr
        WHERE tr.member.id IN :memberIds
        GROUP BY tr.member.id
    """)
    List<MemberRecordCount> countByMemberIds(@Param("memberIds") Collection<Long> memberIds);

    @Query(value = """
        SELECT tr.id AS recordId, tr.createdAt AS createdAt, COUNT(rm.id) AS photoCount
        FROM TravelRecord tr
        LEFT JOIN tr.media rm
        WHERE tr.member.id = :memberId
        GROUP BY tr.id, tr.createdAt
        ORDER BY tr.createdAt DESC, tr.id DESC
    """, countQuery = """
        SELECT COUNT(tr)
        FROM TravelRecord tr
        WHERE tr.member.id = :memberId
    """)
    Page<MemberRecordDetail> findAdminRecordDetailsByMemberId(
            @Param("memberId") Long memberId,
            Pageable pageable
    );

    @Query("SELECT COUNT(rm) FROM RecordMedia rm WHERE rm.travelRecord.member.id = :memberId")
    long countMediaByMemberId(@Param("memberId") Long memberId);

    /**
     * RecordMedia는 TravelRecord 애그리거트 내부 엔티티이므로 루트 리포지토리에서 조회한다.
     * 다른 일지가 이미 쓰고 있는 Object Key인지 판별하는 데 쓴다.
     */
    @Query("""
        SELECT CASE WHEN COUNT(rm) > 0 THEN TRUE ELSE FALSE END
        FROM RecordMedia rm
        WHERE rm.objectKey.value IN :objectKeys
    """)
    boolean existsMediaByObjectKeyIn(@Param("objectKeys") Collection<String> objectKeys);

    /**
     * 목록 화면의 썸네일용. 일지별로 정렬 순서가 가장 앞선 미디어를 고르기 위해 정렬해서 가져온다.
     */
    @Query("""
        SELECT rm
        FROM RecordMedia rm
        WHERE rm.travelRecord.id IN :travelRecordIds
        ORDER BY rm.travelRecord.id ASC, rm.sortOrder ASC, rm.id ASC
    """)
    List<RecordMedia> findMediaByTravelRecordIdIn(
            @Param("travelRecordIds") Collection<Long> travelRecordIds
    );

    @Query("""
        SELECT tr
        FROM TravelRecord tr
        WHERE tr.member.id = :memberId
          AND (:tagId IS NULL OR EXISTS (
              SELECT trt.id
              FROM TravelRecordTag trt
              WHERE trt.travelRecord.id = tr.id
                AND trt.tag.id = :tagId
          ))
    """)
    Page<TravelRecord> findByMemberIdAndOptionalTagId(
            @Param("memberId") Long memberId,
            @Param("tagId") Long tagId,
            Pageable pageable
    );

    @Query("""
        SELECT tr
        FROM TravelRecord tr
        WHERE tr.member.id = :memberId
          AND (
              tr.region.id = :countryId
              OR tr.region.root.id = :countryId
          )
          AND (:tagId IS NULL OR EXISTS (
              SELECT trt.id
              FROM TravelRecordTag trt
              WHERE trt.travelRecord.id = tr.id
                AND trt.tag.id = :tagId
          ))
    """)
    Page<TravelRecord> findByMemberIdAndCountryIdAndOptionalTagId(
            @Param("memberId") Long memberId,
            @Param("countryId") Long countryId,
            @Param("tagId") Long tagId,
            Pageable pageable
    );

    @Query("""
        SELECT tr
        FROM TravelRecord tr
        WHERE tr.member.id = :memberId
          AND (
              tr.region.id = :provinceId
              OR tr.region.parent.id = :provinceId
          )
          AND (:tagId IS NULL OR EXISTS (
              SELECT trt.id
              FROM TravelRecordTag trt
              WHERE trt.travelRecord.id = tr.id
                AND trt.tag.id = :tagId
          ))
    """)
    Page<TravelRecord> findByMemberIdAndProvinceIdAndOptionalTagId(
            @Param("memberId") Long memberId,
            @Param("provinceId") Long provinceId,
            @Param("tagId") Long tagId,
            Pageable pageable
    );

    @Query("""
        SELECT tr
        FROM TravelRecord tr
        WHERE tr.member.id = :memberId
          AND tr.region.id = :regionId
          AND (:tagId IS NULL OR EXISTS (
              SELECT trt.id
              FROM TravelRecordTag trt
              WHERE trt.travelRecord.id = tr.id
                AND trt.tag.id = :tagId
          ))
    """)
    Page<TravelRecord> findByMemberIdAndRegionIdAndOptionalTagId(
            @Param("memberId") Long memberId,
            @Param("regionId") Long regionId,
            @Param("tagId") Long tagId,
            Pageable pageable
    );
}
