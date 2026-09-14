-- 광주광역시와 전라남도에 남아 있던 통합 코드(12xxx)를
-- 클라이언트와 동일한 행정표준코드로 정규화한다.
-- Region ID와 여행 기록의 region_id는 유지하고 코드만 변경한다.

UPDATE region district
    JOIN region province ON province.id = district.parent_id
SET district.region_code = CASE district.region_code
                                WHEN '12210' THEN '29110'
                                WHEN '12240' THEN '29140'
                                WHEN '12270' THEN '29155'
                                WHEN '12300' THEN '29170'
                                WHEN '12330' THEN '29200'
    END
WHERE province.region_type = 'PROVINCE'
  AND province.region_code = '29'
  AND district.region_type = 'DISTRICT'
  AND district.region_code IN ('12210', '12240', '12270', '12300', '12330');

UPDATE region district
    JOIN region province ON province.id = district.parent_id
SET district.region_code = CASE district.region_code
                                WHEN '12110' THEN '46110'
                                WHEN '12130' THEN '46130'
                                WHEN '12150' THEN '46150'
                                WHEN '12170' THEN '46170'
                                WHEN '12190' THEN '46230'
                                WHEN '12710' THEN '46710'
                                WHEN '12720' THEN '46720'
                                WHEN '12730' THEN '46730'
                                WHEN '12740' THEN '46770'
                                WHEN '12750' THEN '46780'
                                WHEN '12760' THEN '46790'
                                WHEN '12770' THEN '46800'
                                WHEN '12780' THEN '46810'
                                WHEN '12790' THEN '46820'
                                WHEN '12800' THEN '46830'
                                WHEN '12810' THEN '46840'
                                WHEN '12820' THEN '46860'
                                WHEN '12830' THEN '46870'
                                WHEN '12840' THEN '46880'
                                WHEN '12850' THEN '46890'
                                WHEN '12860' THEN '46900'
                                WHEN '12870' THEN '46910'
    END
WHERE province.region_type = 'PROVINCE'
  AND province.region_code = '46'
  AND district.region_type = 'DISTRICT'
  AND district.region_code IN (
      '12110', '12130', '12150', '12170', '12190',
      '12710', '12720', '12730', '12740', '12750', '12760',
      '12770', '12780', '12790', '12800', '12810', '12820',
      '12830', '12840', '12850', '12860', '12870'
  );
