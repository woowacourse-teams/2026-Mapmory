# 장소 검색과 여행 기록 연결

## 사용 흐름

1. 로그인한 사용자가 `GET /api/v1/places/search?query=한강공원`을 호출한다. `data`는 `placeId`, `name`, `address`, `attribution`, `attributionUrl`이 담긴 후보 배열이다.
2. 후보를 고르면 `GET /api/v1/places/{placeId}`를 호출한다. 응답의 `suggestedRegion`에 국가·시도·시군구 코드와 이름이 있다. 경계에서 찾지 못하면 `suggestedRegion`은 `null`, `manualRegionRequired`는 `true`다.
3. 기존 `POST /api/v1/travel-records` 요청에 `placeId`만 추가하면 서버가 추천 지역을 자동 적용한다. 사용자가 추천 지역을 수정한 경우 `countryCode`, `provinceCode`, `districtCode`를 함께 보낸다. 국외 장소는 국가 코드를 자동 적용한다.
4. 서버가 장소 ID를 제공자에 다시 조회해 국가를 검증하고 장소 ID와 출처를 저장한다. Geoapify 장소는 이름도 저장하지만 Google 장소는 약관상 이름을 저장하지 않는다(`placeName`이 `null`). 요청 국가와 장소 국가가 다르면 `PLACE_COUNTRY_MISMATCH`로 거절한다. 상세 응답에는 `placeProvider`, `placeId`, `placeName`, `placeAttribution`, `placeAttributionUrl`이 포함된다.

`TravelRecord`는 이 다섯 값을 `RecordedPlace` 값 객체 하나로 다룬다. DB 컬럼과 API 응답 필드는 기존 형태를 유지한다.
`place/application`에는 검색·선택 서비스와 제공자 중립 모델이 있고, 외부 검색 및 지역 판정 계약은 `application/port`에 있다. `infrastructure/PlaceLookupRouter`가 `infrastructure/google`과 `infrastructure/geoapify` 중 제공자를 고르고, `infrastructure/region`이 지역 판정 포트를 구현한다. `web`은 API와 응답 변환을 담당한다. 여행 기록 서비스는 application의 포트와 모델만 참조한다. 제공자 코드(`GOOGLE`, `GEOAPIFY`)는 장소 ID 형식으로 라우터가 정한다.

예시 저장 요청의 장소·지역 부분:

```json
{
  "placeId": "선택한 장소 ID",
  "title": "한강 산책",
  "startDate": "2026-09-30",
  "objectKeys": []
}
```

`placeId`가 없으면 기존 지역 기반 기록 생성·수정 방식이 그대로 동작한다. `PUT`은 전체 수정이므로 `placeId`를 빼면 기존 장소 연결이 제거된다.

자동 판정할 수 없으면 저장 API가 `REGION_REQUIRED`를 반환한다. 이때 `GET /api/v1/places/{placeId}`의 `manualRegionRequired`도 `true`이므로 화면에서 사용자가 지역을 고르게 한다.

## Google Places Autocomplete

`GOOGLE_PLACES_API_KEY`가 있고 클라이언트가 `sessionToken`을 보내면 검색은 [Places API (New) Autocomplete](https://developers.google.com/maps/documentation/places/web-service/place-autocomplete)를 쓴다. Geoapify와 달리 한글 앞부분("경복" → 경복궁)으로도 장소를 찾는다.

- **누가 Google을 쓰나**: `sessionToken`을 보내는 앱 버전만 쓴다. 이전 앱은 출처를 "Powered by Geoapify"로 고정해 그리므로, Google 결과를 받으면 표기 정책을 지킬 수 없다. 그래서 토큰이 없는 요청은 키가 있어도 Geoapify로 검색한다. 선택(`GET /places/{placeId}`)과 기록 저장은 장소 ID 형식으로 제공자를 고른다. 소문자 16진수는 Geoapify, 그 밖은 Google이다. Google 키를 빼면 Google 장소 조회는 `PLACE_PROVIDER_UNAVAILABLE`(503)이 된다.
- **저장하지 않는 것**: 검색은 사진을 찾기 위한 도구다. Google 장소는 장소 ID, 출처(`Google Maps`), 우리 지역 코드만 저장하고 이름은 저장하지 않는다(`placeName`이 `null`). place_id는 저장이 허용된다. 이름·주소 저장은 금지다([Maps Platform ToS 3.2.3(a)(iii), (b)](https://cloud.google.com/maps-platform/terms), [Service Specific Terms §14, A.3](https://cloud.google.com/maps-platform/terms/maps-service-terms)).
- **좌표를 받지 않는 이유**: Places 좌표를 경계 판정(point-in-polygon)의 입력으로 쓰는 것은 금지다([ToS 3.2.3(c)(iv)](https://cloud.google.com/maps-platform/terms)). 그래서 Place Details는 `id,addressComponents`만 요청한다. 국가는 `country` 구성요소에서 받는다. 국내 시·군·구는 `administrative_area_level_1`(시·도)과 `administrative_area_level_2`·`locality`·`sublocality_level_1` 이름을 DB 지역 이름과 맞춰 추천한다(`KoreanAddressRegionMatcher`). 시·도는 줄임 이름("강원도" = "강원특별자치도")으로 비교한다. 일반구가 있는 시는 시 단위(V18)로 맞춘다. 맞추지 못하면 `manualRegionRequired`가 `true`다.
- **지도**: Google 콘텐츠를 Google이 아닌 지도와 함께 쓰면 안 된다(§14.2). Google 장소명이나 좌표를 앱 지도에 표시하지 않는다. 지도에는 사진 GPS와 우리 지역 데이터만 쓴다.
- **출처 표기**: 후보 목록의 위나 아래, 같은 영역 안에 Google Maps 로고(높이 16~19dp)를 둔다. 공간이 부족하면 `Google Maps` 글자(12~16sp, 번역 금지)로 대신할 수 있다([Places 정책](https://developers.google.com/maps/documentation/places/web-service/policies)). Google 결과 옆에 "Powered by Geoapify"를 함께 두면 안 된다.
- **이름 표시**: 선택 응답의 `name`은 Google 장소에서 `null`이다. 클라이언트는 고른 후보의 `name`을 표시한다. `displayName`(Pro 필드)을 요청하지 않아 세션이 Essentials로 끝난다.
- **sessionToken**: 검색을 시작할 때 UUID(36자)를 만들어 검색마다 보내고, 후보를 고를 때 같은 토큰을 선택 API에 보낸다. 선택 후나 검색을 지울 때는 새 토큰을 만든다. 서버는 영문·숫자·`-`·`_` 36자 이하만 받는다. Google 제한이 36자다.
- **과금**: Essentials 상세 조회로 끝난 세션은 Autocomplete 12번째 요청까지 요청당(Autocomplete Requests, 월 10,000건 무료), 13번째부터 무료다. 상세 조회는 Place Details Essentials(월 10,000건 무료)다. 기록 저장 때 재조회도 Essentials다([세션 과금](https://developers.google.com/maps/documentation/places/web-service/session-pricing)). Google 호출은 Geoapify와 별도 한도(`google.places.rate-limit`, 자동완성·상세 각각 UTC 하루 300회, 초당 4회)로 막는다. 회원별 검색·선택 한도는 공통이다. 실제 비용 상한은 Google Cloud 콘솔의 할당량으로도 걸어 둔다.
- **국가 코드**: Google은 괌·사이판·홍콩·마카오를 별도 국가 코드(GU·MP·HK·MO)로 주며, 그대로 쓴다. DB 국가 목록(V6)에는 있지만 앱 지역 목록(`GeneratedWorldMapData`)에는 아직 없다.
- **오류**: 상세 조회의 404와, API 키 오류가 아닌 400은 `PLACE_NOT_FOUND`다. 잘못된 키(400 `API_KEY_*`)를 포함한 나머지와 검색 실패는 `PLACE_PROVIDER_UNAVAILABLE`이다.
- **약관 고지**: 앱 이용약관과 개인정보 처리방침에 Google 서비스 약관과 개인정보처리방침을 포함해야 한다(Places 정책).

## 설정과 데이터

- 서버 환경 변수 `GOOGLE_PLACES_API_KEY`(선택)와 `GEOAPIFY_API_KEY`를 설정한다. Google 키가 없으면 Geoapify로 검색한다. 사용할 제공자의 키가 없으면 장소 API가 `PLACE_PROVIDER_UNAVAILABLE`(503)을 반환한다. Google 키는 Google Cloud에서 Places API (New)만 호출하도록 제한하고 결제 계정이 연결되어 있어야 한다.
- 프런트엔드는 입력마다 호출하기보다 짧은 입력 지연을 적용한다. 검색 후보는 최대 10개다.
- Geoapify 호출 제한은 Bucket4j와 MySQL로 관리한다. 회원별 검색은 1분에 20회, 전체 검색은 UTC 날짜별 2,000회까지 허용한다. 후보 선택은 회원별 하루 100회, 전체 하루 400회까지 허용한다. 검색·선택·기록 저장에서 발생하는 Geoapify 요청은 합쳐서 UTC 날짜별 2,800회, 초당 최대 4회로 제한한다. 검색과 선택이 각 한도에 도달해도 기록 저장용으로 최소 400회가 남는다. 짧은 시간의 초과나 회원 한도는 `PLACE_RATE_LIMITED`(429), 하루 검색 한도는 `PLACE_SEARCH_BUDGET_EXHAUSTED`(429), 하루 선택 한도는 `PLACE_SELECTION_BUDGET_EXHAUSTED`(429), 전체 한도는 `PLACE_PROVIDER_BUDGET_EXHAUSTED`(503)로 응답한다. 이 값은 앱에서 사용하는 Geoapify 호출만 계산하며, 같은 키를 다른 곳에서 호출하면 실제 사용량은 달라진다.
- 국내 시군구 추천은 앱의 `korea-districts-*.json` 17개를 서버에 복사해 JTS 점 포함 판정으로 계산한다. 장소 선택 로직은 `DistrictLocator` 인터페이스에만 의존하므로 경계 데이터나 판정 방식을 교체할 수 있다. 이 파일은 주로 KOSTAT 2018 경계를 단순화한 자료이고 일부 인천 변경을 보정했다. **추천값**이므로 사용자에게 지역 수정 경로를 제공해야 한다. 원본 및 생성 이력은 `client/docs/map-data.md`를 참고한다.
- 지역 경계 JSON이 없거나, 좌표가 경계 밖이거나, 여러 지역이 겹치면 수동 선택으로 넘어간다. 경계 파일 일부가 빠지면 서버에 경고를 남긴다. 해외는 DB에 있는 국가 코드가 추천된다.
- 자동완성은 장소명 키워드 검색을 보장하지 않는다. 실제 `한강공원` 검색에서는 공원보다 입구가 주로 반환되었고, `여의도한강공원`은 공원 후보가 반환되었다. 후보 품질은 사용 흐름에서 확인한 뒤 검색 방식을 조정한다.
- Geoapify 결과와 저장한 장소명을 표시할 때 출처 표기를 UI에 제공한다. OpenStreetMap 표기는 항상 필요하고, Geoapify 무료 플랜에서는 `Powered by Geoapify` 링크도 필요하다. 응답의 `attribution`/`attributionUrl`은 원본 데이터 출처용이다. [Geoapify 이용 조건](https://www.geoapify.com/terms-and-conditions/), [Address Autocomplete](https://apidocs.geoapify.com/docs/geocoding/address-autocomplete/), [Place Details](https://apidocs.geoapify.com/docs/place-details/).

## 확인 범위

Google·Geoapify 실호출에는 유효한 API 키가 필요하다. 단위 테스트는 Google 자동완성·필드 마스크·주소 기반 지역 추천·오류 변환, 토큰 기반 제공자 선택, Google 하루 한도(통합 테스트), 검색 응답, 장소 ID 재조회, 경계 판정, 기록 저장 및 오류 흐름을 모의 응답으로 검증한다.
