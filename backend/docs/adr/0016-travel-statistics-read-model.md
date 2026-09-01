# ADR 0016. 여행 통계 읽기 모델과 지역 집계 단계

- 상태: 채택
- 날짜: 2026-08-31
- 관련: ADR 0008, ADR 0009, `TravelStatisticsRepository`

---

## 문제

마이페이지에서 전체 여행 기록 수, 첨부 미디어 수, 방문 국가와 국내 방문 지역, 자주 기록한
지역을 한 번에 보여줄 필요가 있다. 이 값은 모두 `travel_record`, `record_media`, `region`의
파생 값이므로 별도 통계 엔티티에 저장하면 기록 생성·수정·삭제와 통계 값 사이의 정합성을 추가로
관리해야 한다.

현재 여행 기록의 지역 정밀도는 국가에 따라 다르다.

- 대한민국 기록은 `DISTRICT` Region에 저장한다.
- 해외 기록은 MVP에서 `COUNTRY` Region에 저장한다.

따라서 인기 지역을 하나의 목록으로 반환하려면 대한민국과 해외에 서로 다른 표시 단계를 적용해야
한다. 이 비대칭을 숨기면 나중에 해외 `PROVINCE`·`DISTRICT` 저장을 지원할 때 같은 필드의 의미가
조용히 달라질 수 있다.

## 결정

### API와 읽기 모델

- `GET /api/v1/travel-records/statistics`에서 인증된 현재 회원의 전체 기간 통계를 반환한다.
- 성공 응답은 기존 API와 같이 `data`로 감싼다.
- 통계는 별도 엔티티나 테이블로 저장하지 않고 원본 테이블을 실시간 집계하는 읽기 모델로 구현한다.
- 집계 쿼리와 응답 조합은 `travelrecord.statistics` 패키지에 둔다.
- Service는 HTTP 응답 DTO가 아닌 `TravelStatistics` 읽기 모델을 반환하고, Controller가 이를
  `TravelStatisticsResponse`로 변환한다.

### 필드별 집계 규칙

| 필드 | 규칙 |
| --- | --- |
| `recordCount` | 현재 회원의 `travel_record` 행 수 |
| `mediaCount` | 현재 회원의 여행 기록에 연결된 `record_media` 행 수 |
| `visitedCountryCount` | 기록 Region 자신 또는 `root_id`가 가리키는 `COUNTRY`의 고유 코드 개수 |
| `visitedKoreaDistrictCount` | 국가 루트 코드가 `KR`이고 기록 Region 타입이 `DISTRICT`인 Region의 고유 개수 |
| `visitedCountryCodes` | 방문 국가의 `region_code` 목록. 코드 오름차순이며 개수는 `visitedCountryCount`와 같다 |
| `topRegions` | 현재 지역 표시 단계로 올려 집계한 기록 수 상위 3개 |

`topRegions`의 현재 표시 단계는 다음과 같다.

- 대한민국 `DISTRICT` 기록은 직속 부모 `PROVINCE`로 올려 집계한다.
- 대한민국 `PROVINCE` 직접 기록이 남아 있으면 해당 `PROVINCE`에 집계한다.
- 해외 기록은 국가 루트 `COUNTRY`로 집계한다.
- `recordCount DESC, regionId ASC`로 정렬해 동률 결과를 결정적으로 만든다.
- 각 항목은 이름만 반환하지 않고 `regionId`, `code`, `regionType`을 함께 반환한다.

기록이 없는 회원에게는 오류 대신 숫자 `0`과 빈 배열을 반환한다.

요약 쿼리는 `record_media`를 주 집계에 직접 조인하지 않는다. 한 기록에 미디어가 여러 개면 조인
결과가 증폭되어 다른 카운트에도 `DISTINCT`가 필요해지기 때문이다. 기록·방문 지역은 회원의
`travel_record` 집합에서 한 번 집계하고, 미디어 수만 인덱스 조회가 가능한 스칼라 서브쿼리에서
계산한다. 방문 국가 코드 목록과 상위 지역은 반환 형태와 정렬 기준이 달라 별도 쿼리로 조회한다.
`visitedCountryCount`는 이미 중복 제거된 `visitedCountryCodes`의 크기로 계산해 같은 국가 집계를 두 번
수행하지 않고 두 필드가 항상 일치하도록 한다.

## 해외 지역 확장 시 재검토

현재 `topRegions`는 대한민국의 시·도와 해외 국가를 같은 목록에서 비교한다. 이는 해외 기록이 국가
단위뿐인 MVP를 위한 의도적인 임시 규칙이며 모든 국가에 일반화된 통계 모델이 아니다.

해외 `PROVINCE` 또는 `DISTRICT` 저장을 허용하는 작업을 시작할 때 다음 항목을 함께 재검토한다.

1. 모든 국가를 동일한 Region 단계에서 비교할지, `topCountries`와 국가별 `topSubregions`로 분리할지
   결정한다.
2. 대한민국 전용 `visitedKoreaDistrictCount`를 국가별 하위 지역 통계 구조로 대체할지 결정한다.
3. 기존 필드의 의미가 달라지면 응답을 조용히 변경하지 않고 새 필드 또는 API 버전을 사용한다.
4. 국가별 행정 단계 차이를 `region_code` 접두사로 추론하지 않고 `region_type`, `parent_id`,
   `root_id`와 별도 국가별 정책으로 처리한다.
5. 외국 도시 이름을 인기 지역에 노출하려면 먼저 해당 도시를 Region으로 저장하는 쓰기 계약과
   데이터 마이그레이션을 설계한다.

이 재검토가 끝나기 전에는 해외 하위 Region 데이터가 존재하더라도 현재 통계에서는 국가 루트로
올려 집계한다.

## 테스트 범위

- Controller MVC 테스트로 인증과 JSON 계약을 검증한다.
- Service 단위 테스트로 Projection을 응답 DTO로 변환하는 흐름을 검증한다.
- Repository 쿼리는 MySQL Testcontainers에서 다른 회원 제외, 중복 제거, 국내·해외 표시 단계와
  상위 3개 정렬을 검증한다.

## 결과

- 기록 변경과 통계가 별도 동기화 작업 없이 즉시 일치한다.
- 현재 국내 상세·해외 국가 단위 저장 정책에서 마이페이지가 필요한 통계를 한 번에 조회한다.
- 대한민국 중심의 임시 집계 규칙과 해외 지역 확장 시의 변경 조건이 명시적으로 남는다.
- 데이터가 커져 실시간 집계가 목표 응답 시간을 만족하지 못하면 실행 계획과 운영 메트릭을 근거로
  인덱스 또는 사전 집계 읽기 모델을 별도 ADR에서 결정한다.
