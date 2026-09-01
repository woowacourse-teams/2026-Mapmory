package com.mapmory.backend.travelrecord;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface TravelRecordRepository extends JpaRepository<TravelRecord, Long> {

    Optional<TravelRecord> findByIdAndMemberId(Long id, Long memberId);

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
