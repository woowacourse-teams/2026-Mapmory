# 장소 검색과 여행 기록 연결

## 사용 흐름

1. 로그인한 사용자가 `GET /api/v1/places/search?query=한강공원`을 호출한다. `data`는 `placeId`, `name`, `address`, `attribution`, `attributionUrl`이 담긴 후보 배열이다.
2. 후보를 고르면 `GET /api/v1/places/{placeId}`를 호출한다. 응답의 `suggestedRegion`에 국가·시도·시군구 코드와 이름이 있다. 경계에서 찾지 못하면 `suggestedRegion`은 `null`, `manualRegionRequired`는 `true`다.
3. 기존 `POST /api/v1/travel-records` 요청에 `placeId`만 추가하면 서버가 추천 지역을 자동 적용한다. 사용자가 추천 지역을 수정한 경우 `countryCode`, `provinceCode`, `districtCode`를 함께 보낸다. 국외 장소는 국가 코드를 자동 적용한다.
4. 서버가 장소 ID를 Geoapify에 다시 조회해 장소 이름과 출처를 저장한다. 요청 국가와 장소 국가가 다르면 `PLACE_COUNTRY_MISMATCH`로 거절한다. 상세 응답에는 `placeProvider`, `placeId`, `placeName`, `placeAttribution`, `placeAttributionUrl`이 포함된다.

`TravelRecord`는 이 다섯 값을 `RecordedPlace` 값 객체 하나로 다룬다. DB 컬럼과 API 응답 필드는 기존 형태를 유지한다.
`place/application`에는 검색·선택 서비스와 제공자 중립 모델이 있고, 외부 검색 및 지역 판정 계약은 `application/port`에 있다. `infrastructure/geoapify`와 `infrastructure/region`이 포트를 구현한다. `web`은 API와 응답 변환을 담당한다. 여행 기록 서비스는 application의 포트와 모델만 참조한다. 제공자 코드는 포트 구현체가 제공한다.

예시 저장 요청의 장소·지역 부분:

```json
{
  "placeId": "Geoapify의 장소 ID",
  "title": "한강 산책",
  "startDate": "2026-09-30",
  "objectKeys": []
}
```

`placeId`가 없으면 기존 지역 기반 기록 생성·수정 방식이 그대로 동작한다. `PUT`은 전체 수정이므로 `placeId`를 빼면 기존 장소 연결이 제거된다.

자동 판정할 수 없으면 저장 API가 `REGION_REQUIRED`를 반환한다. 이때 `GET /api/v1/places/{placeId}`의 `manualRegionRequired`도 `true`이므로 화면에서 사용자가 지역을 고르게 한다.

## 설정과 데이터

- 서버 환경 변수 `GEOAPIFY_API_KEY`를 설정한다. 키가 없으면 장소 API가 `PLACE_PROVIDER_UNAVAILABLE`(503)을 반환한다.
- 프런트엔드는 입력마다 호출하기보다 짧은 입력 지연을 적용한다. 검색 후보는 최대 10개다.
- Geoapify 호출 제한은 Bucket4j와 MySQL로 관리한다. 회원별 검색은 1분에 20회, 전체 검색은 UTC 날짜별 2,000회까지 허용한다. 후보 선택은 회원별 하루 100회, 전체 하루 400회까지 허용한다. 검색·선택·기록 저장에서 발생하는 Geoapify 요청은 합쳐서 UTC 날짜별 2,800회, 초당 최대 4회로 제한한다. 검색과 선택이 각 한도에 도달해도 기록 저장용으로 최소 400회가 남는다. 짧은 시간의 초과나 회원 한도는 `PLACE_RATE_LIMITED`(429), 하루 검색 한도는 `PLACE_SEARCH_BUDGET_EXHAUSTED`(429), 하루 선택 한도는 `PLACE_SELECTION_BUDGET_EXHAUSTED`(429), 전체 한도는 `PLACE_PROVIDER_BUDGET_EXHAUSTED`(503)로 응답한다. 이 값은 앱에서 사용하는 Geoapify 호출만 계산하며, 같은 키를 다른 곳에서 호출하면 실제 사용량은 달라진다.
- 국내 시군구 추천은 앱의 `korea-districts-*.json` 17개를 서버에 복사해 JTS 점 포함 판정으로 계산한다. 장소 선택 로직은 `DistrictLocator` 인터페이스에만 의존하므로 경계 데이터나 판정 방식을 교체할 수 있다. 이 파일은 주로 KOSTAT 2018 경계를 단순화한 자료이고 일부 인천 변경을 보정했다. **추천값**이므로 사용자에게 지역 수정 경로를 제공해야 한다. 원본 및 생성 이력은 `client/docs/map-data.md`를 참고한다.
- 지역 경계 JSON이 없거나, 좌표가 경계 밖이거나, 여러 지역이 겹치면 수동 선택으로 넘어간다. 경계 파일 일부가 빠지면 서버에 경고를 남긴다. 해외는 DB에 있는 국가 코드가 추천된다.
- 자동완성은 장소명 키워드 검색을 보장하지 않는다. 실제 `한강공원` 검색에서는 공원보다 입구가 주로 반환되었고, `여의도한강공원`은 공원 후보가 반환되었다. 후보 품질은 사용 흐름에서 확인한 뒤 검색 방식을 조정한다.
- Geoapify 결과와 저장한 장소명을 표시할 때 출처 표기를 UI에 제공한다. OpenStreetMap 표기는 항상 필요하고, Geoapify 무료 플랜에서는 `Powered by Geoapify` 링크도 필요하다. 응답의 `attribution`/`attributionUrl`은 원본 데이터 출처용이다. [Geoapify 이용 조건](https://www.geoapify.com/terms-and-conditions/), [Address Autocomplete](https://apidocs.geoapify.com/docs/geocoding/address-autocomplete/), [Place Details](https://apidocs.geoapify.com/docs/place-details/).

## 확인 범위

Geoapify 실호출에는 유효한 API 키가 필요하다. 단위 테스트는 검색 응답, 장소 ID 재조회, 경계 판정, 기록 저장 및 오류 흐름을 모의 응답으로 검증한다.
