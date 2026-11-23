package com.mapmory.backend.travelrecord.mapsummary.repository;

import com.mapmory.backend.travelrecord.domain.TravelRecord;
import java.util.List;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

/**
 * 기록은 자신의 지역, 바로 위 지역(parent), 국가(root)에 함께 센다.
 * 목록 필터(TravelRecordRepository.findByMemberIdInRegion)와 같은 포함 관계다.
 */
public interface RegionMapSummaryRepository extends Repository<TravelRecord, Long> {

    @Query(value = """
            WITH summary_region AS (
                SELECT region.id,
                       region.parent_id,
                       region.region_code,
                       region.name,
                       region.region_type
                FROM region
                WHERE (:parentRegionId IS NULL AND region.parent_id IS NULL)
                   OR region.parent_id = :parentRegionId
            ),
            member_record AS (
                SELECT summary_region.id AS summary_region_id,
                       COUNT(tr.id) AS record_count
                FROM travel_record tr
                JOIN region recorded_region ON recorded_region.id = tr.region_id
                JOIN summary_region
                  ON summary_region.id = recorded_region.id
                  OR summary_region.id = recorded_region.parent_id
                  OR summary_region.id = recorded_region.root_id
                WHERE tr.member_id = :memberId
                  AND (:tagId IS NULL OR EXISTS (
                      SELECT 1
                      FROM travel_record_tag trt
                      WHERE trt.travel_record_id = tr.id
                        AND trt.tag_id = :tagId
                  ))
                GROUP BY summary_region.id
            )
            SELECT summary_region.id AS regionId,
                   summary_region.parent_id AS parentRegionId,
                   summary_region.region_code AS regionCode,
                   summary_region.name AS name,
                   summary_region.region_type AS regionType,
                   member_record.record_count AS recordCount
            FROM summary_region
            JOIN member_record
              ON member_record.summary_region_id = summary_region.id
            ORDER BY summary_region.region_code
            """, nativeQuery = true)
    List<RegionMapSummaryQueryResult> findRegionMapSummaries(
            @Param("memberId") Long memberId,
            @Param("parentRegionId") Long parentRegionId,
            @Param("tagId") Long tagId
    );

    /**
     * 국가·시도·시군구를 한 번에 센다. 세는 규칙은 {@link #findRegionMapSummaries}와 같다.
     *
     * <p>먼저 회원 기록을 저장된 지역별로 센다(많아야 지역 수만큼). 그 수를 자기 지역, 바로 위 지역, 국가에 더한다.
     * 시도 기록은 parent_id와 root_id가 모두 국가이므로 국가에는 한 번만 더한다.
     * region과 OR로 조인하면 MySQL이 조건 없는 해시 조인(기록 수 × 지역 수)을 고르므로 그렇게 하지 않는다.
     *
     * <p>단계(국가 → 시도 → 시군구) 순서, 같은 단계에서는 코드 순서로 준다.
     */
    @Query(value = """
            WITH recorded AS (
                SELECT tr.region_id,
                       COUNT(*) AS record_count
                FROM travel_record tr
                WHERE tr.member_id = :memberId
                  AND (:tagId IS NULL OR EXISTS (
                      SELECT 1
                      FROM travel_record_tag trt
                      WHERE trt.travel_record_id = tr.id
                        AND trt.tag_id = :tagId
                  ))
                GROUP BY tr.region_id
            ),
            counted AS (
                SELECT recorded_region.id AS summary_region_id,
                       recorded.record_count
                FROM recorded
                JOIN region recorded_region ON recorded_region.id = recorded.region_id
                UNION ALL
                SELECT recorded_region.parent_id,
                       recorded.record_count
                FROM recorded
                JOIN region recorded_region ON recorded_region.id = recorded.region_id
                WHERE recorded_region.parent_id IS NOT NULL
                  AND recorded_region.parent_id <> recorded_region.id
                UNION ALL
                SELECT recorded_region.root_id,
                       recorded.record_count
                FROM recorded
                JOIN region recorded_region ON recorded_region.id = recorded.region_id
                WHERE recorded_region.root_id IS NOT NULL
                  AND recorded_region.root_id <> recorded_region.id
                  AND (recorded_region.parent_id IS NULL OR recorded_region.root_id <> recorded_region.parent_id)
            )
            SELECT summary_region.id AS regionId,
                   summary_region.parent_id AS parentRegionId,
                   summary_region.region_code AS regionCode,
                   summary_region.name AS name,
                   summary_region.region_type AS regionType,
                   CAST(SUM(counted.record_count) AS SIGNED) AS recordCount
            FROM counted
            JOIN region summary_region ON summary_region.id = counted.summary_region_id
            GROUP BY summary_region.id
            ORDER BY CASE summary_region.region_type
                         WHEN 'COUNTRY' THEN 0
                         WHEN 'PROVINCE' THEN 1
                         ELSE 2
                     END,
                     summary_region.region_code
            """, nativeQuery = true)
    List<RegionMapSummaryQueryResult> findAllRegionMapSummaries(
            @Param("memberId") Long memberId,
            @Param("tagId") Long tagId
    );
}
