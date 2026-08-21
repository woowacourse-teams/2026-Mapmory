package com.mapmory.backend.travelrecord;

import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface TravelRecordRepository extends JpaRepository<TravelRecord, Long> {

    Optional<TravelRecord> findByIdAndMemberId(Long id, Long memberId);

    Page<TravelRecord> findByMemberId(Long memberId, Pageable pageable);

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
    """)
    Page<TravelRecord> findByMemberIdAndCountryId(@Param("memberId") Long memberId,
                                                   @Param("countryId") Long countryId,
                                                   Pageable pageable);

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
    """)
    Page<TravelRecord> findByMemberIdAndProvinceId(@Param("memberId") Long memberId,
                                                   @Param("provinceId") Long provinceId,
                                                   Pageable pageable);

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

    Page<TravelRecord> findByMemberIdAndRegionId(Long memberId, Long regionId, Pageable pageable);

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
