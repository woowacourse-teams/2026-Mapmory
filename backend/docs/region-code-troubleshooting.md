# 일반구와 시 단위 지역 코드 불일치 트러블슈팅

> 기준일: 2026-08-28
> 해결 상태: `V18__canonicalize_city_district_regions.sql`에서 시 단위로 통합 완료

## 1. 증상

V18 적용 전에는 일반구가 없는 시만 클라이언트와 백엔드가 같은 코드를 사용했고, 일반구가 있는 13개 시는 서로 다른 코드를 사용했다.

| 구분 | 클라이언트 | V18 적용 전 백엔드 데이터 |
| --- | --- | --- |
| 수원 | `41110 수원시` | `41111 장안구`, `41113 권선구`, `41115 팔달구`, `41117 영통구` |
| 성남 | `41130 성남시` | `41131 수정구`, `41133 중원구`, `41135 분당구` |
| 화성 | `41590 화성시` | `41591 만세구`, `41593 효행구`, `41595 병점구`, `41597 동탄구` |

발생할 수 있었던 결과는 다음과 같다.

- 클라이언트가 보낸 시 코드에 대해 `404 REGION_NOT_FOUND`가 발생한다.
- 일반구 코드로 저장된 기록이 클라이언트의 시 단위 지도에 표시되지 않는다.
- 같은 시의 방문 횟수가 시 코드와 일반구 코드에 분산된다.
- 지역 필터와 지도 통계가 서로 다른 지역 단위를 사용한다.

## 2. 기대 정책

여행 기록의 대한민국 지역 단위는 다음과 같다.

```text
서울·광역시       → 자치구·군
세종특별자치시    → 세종시
제주특별자치도    → 제주시·서귀포시
일반 도           → 시·군
일반 시의 일반구  → 시 단위로 통합
```

13개 통합 대상과 상세 매핑은 [대한민국 여행 기록 지역 코드표](region-code-table.md)를 따른다.

## 3. 원인

### 클라이언트

클라이언트는 일반 도의 `○○시 ○○구`를 발견하면 코드 마지막 자리를 `0`으로 바꾸고 같은 시의 지도 경계를 합친다.

```text
41111, 41113, 41115, 41117
              ↓
         41110 수원시
```

따라서 선택·표시 단위가 시·군으로 정규화되어 있다.

### 백엔드

백엔드 Region 계층은 `COUNTRY → PROVINCE → DISTRICT` 세 단계이며 `CITY`와 `GU`를 구분하지 않는다.
기존 지역 데이터는 행정 원본의 세부 코드를 넣었기 때문에 일반구가 없는 시와 일반 시의 일반구가 모두 `DISTRICT`가 되었다.

```text
의정부시      → DISTRICT
수원시 권선구 → DISTRICT
서울 강남구   → DISTRICT
```

`RegionResolver`는 요청 코드가 선택한 시·도의 직접 자식인지 정확히 조회한다.
따라서 DB에 없는 `41110`을 `districtCode`로 요청하면, DB에 `41111` 등이 있더라도 같은 수원시로 간주하지 않는다.

## 4. 확인 방법

### 13개 canonical 시 코드 존재 여부

```sql
SELECT region_code, name, region_type
FROM region
WHERE region_code IN (
    '41110', '41130', '41170', '41190', '41270', '41280', '41460',
    '41590', '43110', '44130', '52110', '47110', '48120'
)
ORDER BY region_code;
```

### 폐기 대상 일반구 코드로 저장된 기록 확인

```sql
SELECT r.region_code, r.name, COUNT(tr.id) AS record_count
FROM region r
LEFT JOIN travel_record tr ON tr.region_id = r.id
WHERE r.region_code IN (
    '41111', '41113', '41115', '41117',
    '41131', '41133', '41135',
    '41171', '41173',
    '41192', '41194', '41196',
    '41271', '41273',
    '41281', '41285', '41287',
    '41461', '41463', '41465',
    '41591', '41593', '41595', '41597',
    '43111', '43112', '43113', '43114',
    '44131', '44133',
    '52111', '52113',
    '47111', '47113',
    '48121', '48123', '48125', '48127', '48129'
)
GROUP BY r.region_code, r.name
ORDER BY r.region_code;
```

## 5. 해결 절차

이미 적용된 Flyway 마이그레이션을 수정하지 않고 V18 마이그레이션으로 처리했다.

적용한 처리 순서는 다음과 같다.

1. 13개 canonical 시 Region을 각 시·도 아래 `DISTRICT`로 추가한다.
2. 폐기 대상 일반구를 참조하는 `travel_record.region_id`를 대응하는 시 Region ID로 변경한다.
3. 여행 기록이 더 이상 일반구를 참조하지 않는지 확인한다.
4. 폐기 대상 일반구 Region을 삭제한다.
5. 생성·수정·조회·지도 요약 테스트에서 canonical 시 코드만 사용하는지 검증한다.

반드시 기록 이관 후 Region을 삭제한다. `travel_record.region_id`가 Region을 외래키로 참조하므로 순서가 바뀌면 삭제가 실패한다.

## 6. 이관 예시

다음은 V18에서 적용한 이관 방향을 요약한 예시다.

```text
41111 수원시 장안구 ┐
41113 수원시 권선구 ├→ 41110 수원시
41115 수원시 팔달구 ┤
41117 수원시 영통구 ┘
```

기존 기록 유무와 관계없이 V18은 모든 일반구 코드에 대해 코드표의 매핑대로 기록을 먼저 이관한 후 일반구 Region을 제거한다.

## 7. 해결 후 검증 항목

- 13개 canonical 코드가 모두 `DISTRICT`로 존재한다.
- 13개 Region의 `parent_id`가 올바른 시·도 Region을 가리킨다.
- 폐기 대상 일반구 코드가 Region 데이터에 존재하지 않는다.
- 폐기 대상 일반구를 참조하는 여행 기록이 없다.
- `countryCode=KR`, 올바른 `provinceCode`, canonical `districtCode`로 기록 생성이 성공한다.
- 잘못된 시·도와 canonical 코드를 조합하면 `INVALID_REGION_HIERARCHY`가 발생한다.
- 시 단위 목록 필터와 지도 요약에 기존 일반구 기록의 이관 결과가 포함된다.
- 클라이언트 지도 코드와 API 응답 코드가 일치한다.

## 8. 재발 방지

- 새 지역을 추가하기 전에 [지역 코드표](region-code-table.md)의 선택 단위를 확인한다.
- 일반 도의 `○○시 ○○구`는 일반구 Region으로 추가하지 않는다.
- 행정 원본 데이터를 그대로 삽입하지 않고 서비스의 canonical 단위로 정규화한다.
- 지역 마이그레이션 테스트에서 canonical 코드의 존재와 폐기 코드의 부재를 함께 검증한다.
- 지역 코드 접두사로 상위 지역을 추론하지 않고 `parent_id`로 계층을 검증한다.

## 9. 관련 코드와 문서

- 지역 초기 데이터: `src/main/resources/db/migration/V8__insert_location_district.sql`
- Region 통합 마이그레이션: `src/main/resources/db/migration/V10__unify_country_and_location_as_region.sql`
- 일반구 통합 마이그레이션: `src/main/resources/db/migration/V18__canonicalize_city_district_regions.sql`
- 지역 탐색: `src/main/java/com/mapmory/backend/region/RegionResolver.java`
- 여행 기록 지역 검증: `src/main/java/com/mapmory/backend/travelrecord/application/TravelRecordService.java`
- [전체 Region 코드표](region-code-all.md)
- [지역 코드 체계 ADR](adr/0004-region-code-system.md)
- [Region 통합 ADR](adr/0008-unify-country-and-location-as-region.md)
