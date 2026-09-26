-- 광주광역시·전라남도 시군구 코드를 클라이언트의 canonical 행정표준코드와 일치시킨다.
--
-- V8은 전남광주 통합을 선반영한 12xxx 코드를 사용했지만, 클라이언트 지도는
-- 광주 29xxx, 전남 46xxx 기존 코드를 유지한다. Region 행 자체를 UPDATE하여
-- travel_record.region_id가 가리키는 ID와 기존 여행 기록은 그대로 보존한다.

UPDATE region district
    JOIN region province
         ON province.id = district.parent_id
             AND province.region_type = 'PROVINCE'
    JOIN region country
         ON country.id = district.root_id
             AND country.region_type = 'COUNTRY'
             AND country.region_code = 'KR'
    JOIN (
             SELECT '29' AS province_code, '12210' AS previous_code, '29110' AS canonical_code
             UNION ALL SELECT '29', '12240', '29140'
             UNION ALL SELECT '29', '12270', '29155'
             UNION ALL SELECT '29', '12300', '29170'
             UNION ALL SELECT '29', '12330', '29200'
             UNION ALL SELECT '46', '12110', '46110'
             UNION ALL SELECT '46', '12130', '46130'
             UNION ALL SELECT '46', '12150', '46150'
             UNION ALL SELECT '46', '12170', '46170'
             UNION ALL SELECT '46', '12190', '46230'
             UNION ALL SELECT '46', '12710', '46710'
             UNION ALL SELECT '46', '12720', '46720'
             UNION ALL SELECT '46', '12730', '46730'
             UNION ALL SELECT '46', '12740', '46770'
             UNION ALL SELECT '46', '12750', '46780'
             UNION ALL SELECT '46', '12760', '46790'
             UNION ALL SELECT '46', '12770', '46800'
             UNION ALL SELECT '46', '12780', '46810'
             UNION ALL SELECT '46', '12790', '46820'
             UNION ALL SELECT '46', '12800', '46830'
             UNION ALL SELECT '46', '12810', '46840'
             UNION ALL SELECT '46', '12820', '46860'
             UNION ALL SELECT '46', '12830', '46870'
             UNION ALL SELECT '46', '12840', '46880'
             UNION ALL SELECT '46', '12850', '46890'
             UNION ALL SELECT '46', '12860', '46900'
             UNION ALL SELECT '46', '12870', '46910'
         ) code_mapping
         ON code_mapping.province_code = province.region_code
             AND code_mapping.previous_code = district.region_code
SET district.region_code = code_mapping.canonical_code
WHERE district.region_type = 'DISTRICT';
