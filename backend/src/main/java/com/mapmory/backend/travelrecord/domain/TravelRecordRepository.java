package com.mapmory.backend.travelrecord.domain;

import com.mapmory.backend.region.domain.Region;
import com.mapmory.backend.tag.Tag;
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

    /**
     * 상세 응답은 지역을 국가·시도·시군구로 펼쳐 보여주므로 Region의 parent·root까지 필요하다.
     * 둘 다 지연 로딩이고 open-in-view가 꺼져 있어, 여기서 함께 읽어두지 않으면
     * 웹 계층의 DTO 변환에서 LazyInitializationException이 난다.
     */
    @EntityGraph(attributePaths = {"region", "region.parent", "region.root"})
    Optional<TravelRecord> findByIdAndMemberId(Long id, Long memberId);

    /**
     * startDate가 같으면 TravelRecord.id 순서로 고정
     * region.root를 함께 조회해 지연 로딩 문제 방지
     * 개수 제한 없음
     */
    @EntityGraph(attributePaths = {"region", "region.root"})
    @Query("""
        SELECT travelRecord
        FROM TravelRecord travelRecord
        WHERE travelRecord.member.id = :memberId
        ORDER BY travelRecord.period.startDate ASC, travelRecord.id ASC
        """)
    List<TravelRecord> findAllForTravelTimeline(@Param("memberId") Long memberId);

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

    /**
     * TravelRecordTag도 애그리거트 내부 엔티티라 루트 리포지토리에서 조회한다. 상세 화면의 태그를 만든 순서로 읽는다.
     */
    @Query("""
        SELECT trt.tag
        FROM TravelRecordTag trt
        WHERE trt.travelRecord.id = :travelRecordId
        ORDER BY trt.tag.createdAt ASC, trt.tag.id ASC
    """)
    List<Tag> findTagsByTravelRecordId(@Param("travelRecordId") Long travelRecordId);

    /**
     * 목록 화면의 태그용. 한 페이지의 연결을 태그와 함께 한 번에 읽는다.
     */
    @Query("""
        SELECT trt
        FROM TravelRecordTag trt
        JOIN FETCH trt.tag
        WHERE trt.travelRecord.id IN :travelRecordIds
        ORDER BY trt.tag.createdAt ASC, trt.tag.id ASC
    """)
    List<TravelRecordTag> findTagLinksByTravelRecordIdIn(
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

    /**
     * 지역 안의 기록을 찾는다. 국가는 그 아래 모든 지역, 시·도는 그 아래 시·군·구, 시·군·구는 자신만 포함한다.
     * 하위 지역의 parent는 바로 위 지역이고 root는 국가라서, 단위마다 조건 두 개로 찾도록 쿼리를 나눈다.
     * 지도 요약(RegionMapSummaryRepository)도 같은 포함 관계로 센다.
     */
    default Page<TravelRecord> findByMemberIdInRegion(Long memberId, Region region, Long tagId, Pageable pageable) {
        return switch (region.getRegionType()) {
            case COUNTRY -> findByMemberIdAndCountryIdAndOptionalTagId(memberId, region.getId(), tagId, pageable);
            case PROVINCE -> findByMemberIdAndProvinceIdAndOptionalTagId(memberId, region.getId(), tagId, pageable);
            case DISTRICT -> findByMemberIdAndRegionIdAndOptionalTagId(memberId, region.getId(), tagId, pageable);
        };
    }

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
