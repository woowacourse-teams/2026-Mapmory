package com.mapmory.backend.travelrecord.statistics.repository;

import com.mapmory.backend.travelrecord.TravelRecord;
import java.util.List;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

public interface TravelStatisticsRepository extends Repository<TravelRecord, Long> {

    @Query(value = """
            SELECT COUNT(travel_record.id) AS recordCount,
                   (
                       SELECT COUNT(record_media.id)
                       FROM record_media
                       JOIN travel_record media_record
                         ON media_record.id = record_media.travel_record_id
                       WHERE media_record.member_id = :memberId
                   ) AS mediaCount,
                   COUNT(DISTINCT CASE
                       WHEN country_region.region_code = 'KR'
                        AND recorded_region.region_type = 'DISTRICT'
                       THEN recorded_region.id
                   END) AS visitedKoreaDistrictCount
            FROM travel_record
            JOIN region recorded_region
              ON recorded_region.id = travel_record.region_id
            JOIN region country_region
              ON country_region.id = COALESCE(recorded_region.root_id, recorded_region.id)
            WHERE travel_record.member_id = :memberId
            """, nativeQuery = true)
    TravelStatisticsSummaryQueryResult findSummary(@Param("memberId") Long memberId);

    @Query(value = """
            SELECT DISTINCT country_region.region_code
            FROM travel_record
            JOIN region recorded_region
              ON recorded_region.id = travel_record.region_id
            JOIN region country_region
              ON country_region.id = COALESCE(recorded_region.root_id, recorded_region.id)
            WHERE travel_record.member_id = :memberId
            ORDER BY country_region.region_code
            """, nativeQuery = true)
    List<String> findVisitedCountryCodes(@Param("memberId") Long memberId);

    @Query(value = """
            SELECT aggregate_region.id AS regionId,
                   aggregate_region.region_code AS regionCode,
                   aggregate_region.region_type AS regionType,
                   aggregate_region.name AS name,
                   COUNT(travel_record.id) AS recordCount
            FROM travel_record
            JOIN region recorded_region
              ON recorded_region.id = travel_record.region_id
            JOIN region country_region
              ON country_region.id = COALESCE(recorded_region.root_id, recorded_region.id)
            JOIN region aggregate_region
              ON aggregate_region.id = CASE
                  WHEN country_region.region_code = 'KR'
                   AND recorded_region.region_type = 'DISTRICT'
                  THEN recorded_region.parent_id
                  WHEN country_region.region_code = 'KR'
                   AND recorded_region.region_type = 'PROVINCE'
                  THEN recorded_region.id
                  ELSE country_region.id
              END
            WHERE travel_record.member_id = :memberId
            GROUP BY aggregate_region.id,
                     aggregate_region.region_code,
                     aggregate_region.region_type,
                     aggregate_region.name
            ORDER BY COUNT(travel_record.id) DESC, aggregate_region.id ASC
            LIMIT 3
            """, nativeQuery = true)
    List<TopRegionQueryResult> findTopRegions(@Param("memberId") Long memberId);
}
