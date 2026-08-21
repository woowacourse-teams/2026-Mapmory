package com.mapmory.backend.travelrecord.mapsummary.repository;

import com.mapmory.backend.travelrecord.TravelRecord;
import java.util.List;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

public interface RegionMapSummaryRepository extends Repository<TravelRecord, Long> {

    @Query(value = """
            WITH summary_region AS (
                SELECT region.id,
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
}
