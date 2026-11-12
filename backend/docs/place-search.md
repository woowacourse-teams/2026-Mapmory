# 장소 검색과 여행 기록 연결

## 사용 흐름

1. 로그인한 사용자가 `GET /api/v1/places/search?query=한강공원`을 호출한다. `data`는 `placeId`, `name`, `address`, `attribution`, `attributionUrl`이 담긴 후보 배열이다. 이름이 없거나 ID 형식이 맞지 않는 후보는 고를 수 없어 빼고 응답한다.
2. 후보를 고르면 `GET /api/v1/places/{placeId}`를 호출한다. 응답의 `suggestedRegion`에 국가·시도·시군구 코드와 이름이 있다. 주소로 지역을 찾지 못하면 `suggestedRegion`은 `null`, `manualRegionRequired`는 `true`다.
3. 기존 `POST /api/v1/travel-records` 요청에 `placeId`만 추가하면 서버가 추천 지역을 자동 적용한다. 사용자가 추천 지역을 수정한 경우 `countryCode`, `provinceCode`, `districtCode`를 함께 보낸다. 국외 장소는 국가 코드를 자동 적용한다.
4. 서버가 장소 ID와 출처를 저장한다. Google 장소는 약관상 이름을 저장하지 않는다(`placeName`이 `null`). 요청에 지역이 있으면 다시 조회하지 않고 ID와 출처만 붙인다(`PlaceDetails.withoutLookup`). 다시 받을 값이 국가뿐이고 출처는 상수이기 때문이다. 이때 장소 국가를 모르므로 요청 지역과 맞는지 확인하지 않는다. 요청에 지역이 없으면 3번처럼 다시 조회해 지역을 정한다. 상세 응답에는 `placeProvider`, `placeId`, `placeName`, `placeAttribution`, `placeAttributionUrl`이 포함된다.

`TravelRecord`는 이 다섯 값을 `RecordedPlace` 값 객체 하나로 다룬다. DB 컬럼과 API 응답 필드는 기존 형태를 유지한다.
`place/domain`에는 제공자와 무관한 장소 규칙이 있다. 장소 ID 형식(`PlaceId`), 제공자와 출처(`PlaceProvider`), 검색어(`SearchQuery`), 후보와 상세(`PlaceCandidate`, `PlaceDetails`)다. `place/application`에는 검색·선택 서비스가 있고, 장소 조회 계약은 `application/port`에 있다. 포트는 제공자(`provider()`), 검색(`search`), 상세 조회(`findDetails`)만 내준다. `infrastructure/google/places`의 `GooglePlacesClient`가 이 포트를 구현하고(사진 좌표 → 지역은 `google/geocoding`의 `GoogleGeocodingClient`), 약관·과금·응답 형식 같은 Google 규칙은 `infrastructure/google` 아래에만 둔다. `web`은 API와 응답 변환을 담당한다. 회원별 호출 한도는 인터셉터부터 DB 버킷까지 기능 루트의 `ratelimit` 한 패키지에 있다. 여행 기록 서비스는 application의 포트와 모델만 참조한다. 예전 Geoapify 장소로 저장된 기록은 `placeProvider`가 `GEOAPIFY`로 남는다.

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

검색은 [Places API (New) Autocomplete](https://developers.google.com/maps/documentation/places/web-service/place-autocomplete)를 쓴다. 한글 앞부분("경복" → 경복궁)으로도 장소를 찾는다.

- **sessionToken 필수**: 이전 앱은 출처를 "Powered by Geoapify"로 고정해 그리므로 Google 결과를 받으면 표기 정책을 지킬 수 없다. 그래서 검색은 `sessionToken`을 보내는 앱 버전만 받는다. Google 키를 빼면 장소 검색·선택과 장소를 붙인 기록 저장이 `PLACE_PROVIDER_UNAVAILABLE`(503)이 된다.
- **저장하지 않는 것**: 검색은 사진을 찾기 위한 도구다. Google 장소는 장소 ID, 출처(`Google Maps`), 우리 지역 코드만 저장하고 이름은 저장하지 않는다(`placeName`이 `null`). place_id는 저장이 허용된다. 이름·주소 저장은 금지다([Maps Platform ToS 3.2.3(a)(iii), (b)](https://cloud.google.com/maps-platform/terms), [Service Specific Terms §14, A.3](https://cloud.google.com/maps-platform/terms/maps-service-terms)).
- **좌표를 받지 않는 이유**: Places 좌표를 경계 판정(point-in-polygon)의 입력으로 쓰는 것은 금지다([ToS 3.2.3(c)(iv)](https://cloud.google.com/maps-platform/terms)). 그래서 Place Details는 `id,addressComponents,shortFormattedAddress`만 요청한다(모두 Essentials 등급). 국가는 `country` 구성요소에서 받는다. 국내 시·군·구는 `administrative_area_level_1`(시·도)과 `administrative_area_level_2`·`locality`·`sublocality_level_1` 이름을 DB 지역 이름과 맞춰 추천한다(`KoreanAddressRegionMatcher`). 시·도는 줄임 이름("강원도" = "강원특별자치도")으로 비교한다. 일반구가 있는 시는 시 단위(V18)로 맞춘다. 맞추지 못하면 `manualRegionRequired`가 `true`다. 시·도가 없으면(대천해수욕장) 시·군·구 이름을 전국에서 찾아 한 곳만 맞을 때 쓴다. 구성요소에 국가가 없으면(독도리: 구성요소 "독도리"뿐) 짧은 주소("울릉군 울릉읍 독도리")의 낱말을 더해 같은 방식으로 찾고, 찾으면 그 지역의 국가를 `countryCode`로 응답한다.
- **지도**: Google 콘텐츠를 Google이 아닌 지도와 함께 쓰면 안 된다(§14.2). Google 장소명이나 좌표를 앱 지도에 표시하지 않는다. 지도에는 사진 GPS와 우리 지역 데이터만 쓴다.
- **출처 표기**: 후보 목록의 위나 아래, 같은 영역 안에 Google Maps 로고(높이 16~19dp)를 둔다. 공간이 부족하면 `Google Maps` 글자(12~16sp, 번역 금지)로 대신할 수 있다([Places 정책](https://developers.google.com/maps/documentation/places/web-service/policies)).
- **이름 표시**: 선택 응답의 `name`은 Google 장소에서 `null`이다. 클라이언트는 고른 후보의 `name`을 표시한다. `displayName`(Pro 필드)을 요청하지 않아 세션이 Essentials로 끝난다.
- **sessionToken**: 검색을 시작할 때 UUID(36자)를 만들어 검색마다 보내고, 후보를 고를 때 같은 토큰을 선택 API에 보낸다. 선택 후나 검색을 지울 때는 새 토큰을 만든다. 서버는 영문·숫자·`-`·`_` 36자 이하만 받는다. Google 제한이 36자다.
- **과금**: Essentials 상세 조회로 끝난 세션은 Autocomplete 12번째 요청까지 요청당(Autocomplete Requests, 월 10,000건 무료), 13번째부터 무료다. 상세 조회는 Place Details Essentials(월 10,000건 무료)다. 기록 저장 때 재조회도 Essentials다([세션 과금](https://developers.google.com/maps/documentation/places/web-service/session-pricing)). Places는 Google Cloud에서 지출 상한을 걸 수 없어, 서버의 Google 월 한도(아래 '호출 한도')가 사실상 비용 상한이다. Google Cloud에는 예산 알림만 걸어 둔다.
- **국가 코드**: Google은 괌·사이판·홍콩·마카오를 별도 국가 코드(GU·MP·HK·MO)로 주며, 그대로 쓴다. DB 국가 목록(V6)에는 있지만 앱 지역 목록(`GeneratedWorldMapData`)에는 아직 없다.
- **오류**: 상세 조회의 404와, API 키 오류가 아닌 400은 `PLACE_NOT_FOUND`다. 잘못된 키(400 `API_KEY_*`)를 포함한 나머지와 검색 실패는 `PLACE_PROVIDER_UNAVAILABLE`이다.
- **약관 고지**: 앱 이용약관과 개인정보 처리방침에 Google 서비스 약관과 개인정보처리방침을 포함해야 한다(Places 정책).

## 설정과 데이터

- 서버 환경 변수 `GOOGLE_PLACES_API_KEY`를 설정한다. 키가 없으면 장소 API가 `PLACE_PROVIDER_UNAVAILABLE`(503)을 반환한다. 키는 Google Cloud에서 Places API (New)와 Geocoding API만 호출하도록 제한하고(사진 좌표 → 지역이 같은 키로 Geocoding을 부른다) 결제 계정이 연결되어 있어야 한다.
- 프런트엔드는 입력마다 호출하기보다 짧은 입력 지연을 적용한다.
- 국내 시군구 추천은 Google 주소 구성요소 이름을 DB 지역 이름과 맞춰 계산한다(`KoreanAddressRegionMatcher`). **추천값**이므로 사용자에게 지역 수정 경로를 제공해야 한다. 해외는 DB에 있는 국가 코드가 추천된다.

## 호출 한도

한도는 두 층이다. 들어오는 요청은 회원별로, 나가는 Google 호출은 키 전체로 센다.

- **회원 한도**(`place.rate-limit`, `MemberPlaceRateLimiter`): 한 회원이 많이 부르지 못하게 한다. 검색은 회원별 1분에 20회, 후보 선택은 회원별 UTC 하루 100회다. 서버 여러 대가 함께 세도록 DB(`place_rate_limit_bucket`, Bucket4j MySQL)에서 센다. `PlaceRateLimitWebConfig`가 URL마다 확인할 한도를 정하고, 인터셉터가 컨트롤러에 들어가기 전에 확인한다. 요청 값 검증보다 먼저라 400으로 끝날 요청도 횟수에 들어간다. 넘으면 `PLACE_RATE_LIMITED`(429)다.
- **Google 한도**(`google.places.rate-limit`·`GooglePlacesLimiter`, `google.geocoding.rate-limit`·`GoogleGeocodingLimiter`): 우리 Google 사용량을 API마다 따로 막는다. 각 클라이언트가 Google을 부르기 직전에 확인하므로, 회원 한도를 거치지 않는 기록 저장 재조회도 함께 센다. 자동완성·상세·Geocoding 각각 UTC 달마다 20,000회는 비용 상한이라 서버 전체가 함께 세도록 DB에서 센다. 무료 사용량(SKU마다 월 10,000건)을 넘어도 막지 않고 과금되며, 상한까지 다 쓰면 SKU마다 10,000건이 과금된다. 무료를 넘기 전에 알 수 있도록 9,000번째 호출(`free-alert-at`)에서 ERROR 로그(`event=GOOGLE_PLACES_FREE_USAGE`, 좌표는 `GOOGLE_GEOCODING_FREE_USAGE`)를 서버마다 달에 한 번 남긴다(환불로 같은 개수를 다시 지나도 다시 울리지 않는다). 상한을 올려 배포하면 이미 만든 이번 달 버킷에도 바로 적용된다(Bucket4j 설정 교체, 쓴 개수는 그대로). 낮춘 상한은 다음 달부터 적용된다. 무료 사용량은 매달 1일 태평양 시간 자정에 다시 차서 UTC 달과 몇 시간 어긋나므로 알림을 10,000보다 앞에 둔다([요금 안내](https://developers.google.com/maps/billing-and-pricing/pay-as-you-go)). 분당 한도(서버마다 장소 검색 60회, 사진 좌표 → 지역 20회, 따로 센다)는 몰린 요청이 월 한도를 한꺼번에 쓰지 못하게 하는 값이라 메모리에서 센다. Google 쿼터도 API마다 따로라 서로 나눠 쓸 이유가 없다. 메모리 분당 한도를 먼저 보고 DB 월 한도를 본다. 분당 한도는 `PLACE_RATE_LIMITED`(429), 월 자동완성 한도는 `PLACE_SEARCH_BUDGET_EXHAUSTED`(429), 월 상세·Geocoding 한도는 `PLACE_PROVIDER_BUDGET_EXHAUSTED`(503)다. 월 한도의 80%를 쓴 순간과 다 쓴 순간에 ERROR 로그(`event=GOOGLE_PLACES_BUDGET`, 좌표는 `GOOGLE_GEOCODING_BUDGET`)를 한 번씩 남겨 운영 ERROR 로그 알람(Discord)으로 알린다.
- DB 한도 저장소에 닿지 못하면 사용량을 통제할 수 없으므로 `PLACE_PROVIDER_UNAVAILABLE`(503)로 거절한다. 만료된 버킷은 하루 한 번(UTC 04:30) 정리한다.
- **Google 장애**(`google.places.circuit-breaker`, `google.geocoding.circuit-breaker`): Google 호출은 Apache HttpClient로 연결(TCP+TLS) 400ms, 연결 뒤 응답 1초를 기다린다(`google.connect-timeout`·`read-timeout`, 2026-11-04 dev에서 잰 연결 최대 134ms·응답 p99 약 300ms의 3배쯤). 재시도는 하지 않고, 연결 풀은 API마다 따로 둔다. 풀 크기는 동시 연결 수라 분당 요청 수와 단위가 다르지만, 분당 한도가 한순간에 몰릴 때가 동시 호출의 상한이라 그 수(장소 60, 좌표 20)를 풀 크기로 써 풀에서 줄을 서지 않는다. 끊기면 WARN 로그(`event=GOOGLE_PLACES_TIMEOUT`, `phase=POOL|CONNECT|RESPONSE`)로 어느 단계였는지 남긴다. Google이 멈추면 서킷 브레이커가 Google 한도 확인보다 먼저 막아, 사용자가 1초씩 기다리지 않고 월 한도도 쓰지 않는다. 서버마다 최근 60초 동안 5번 이상 불렀고 그중 절반 이상이 타임아웃·연결 오류·5xx면 60초 동안 열고 바로 `PLACE_PROVIDER_UNAVAILABLE`(503)로 답한다. 4xx는 Google이 답한 것이라 성공으로 세고, 우리 한도 거절은 세지 않는다. 60초가 지나면 3번 시험해 절반 이상 성공하면 닫는다. 닫혀 있다 열릴 때만 ERROR 로그(`event=GOOGLE_PLACES_CIRCUIT`)를 남겨 운영 ERROR 로그 알람(Discord)이 장애 하나에 한 번 울리게 하고, 시험 뒤 다시 열리거나 닫히는 것은 WARN으로 남긴다. 60초는 Google 장애 시간 자료가 없어 정한 값이라 실제로 열리는 것을 보고 고친다. 사진 좌표 → 지역(`GoogleGeocodingClient`, Geocoding API v4)은 같은 값으로 자기 서킷 브레이커를 따로 둔다(`event=GOOGLE_GEOCODING_CIRCUIT`, 타임아웃 `GOOGLE_GEOCODING_TIMEOUT`, 거절 `GOOGLE_GEOCODING_REJECTED`). 한쪽 장애나 몰림이 다른 쪽을 막지 않는다. 두 API가 같이 쓰는 건 키와 타임아웃(`GoogleApiProperties`: `google.api-key`, `google.connect-timeout`, `google.read-timeout`)과 주소 구성요소 해석(`google/GoogleAddressMapper`)뿐이다. 코드는 API마다 자기 폴더에 가둔다. `places/`에는 `GooglePlacesConfig`(RestClient·연결 풀·서킷 브레이커·한도, `google.places.*`), 호출 순서(서킷 브레이커 → 한도 확보 → 호출 → 과금 안 된 실패면 환불 → 우리 에러·로그로 바꾸기)를 맡는 `GooglePlacesGuard`, 엔드포인트 명세(`GooglePlacesEndpoint`: 자동완성·상세, 월 한도 이름과 다 썼을 때의 에러, 404를 PLACE_NOT_FOUND로 볼지. 엔드포인트를 더하면 이 enum 한 줄과 yaml `google.places.rate-limit.per-month` 한 줄만 더한다), `GooglePlacesLimiter`가 있다. `geocoding/`에는 같은 역할의 `GoogleGeocodingConfig`(`google.geocoding.*`)·`GoogleGeocodingGuard`·`GoogleGeocodingLimiter`가 있다. 두 폴더는 서로를 import하지 않는다. API를 모르는 도구는 `google/resilience`에 있어 각 API가 자기 것을 하나씩 만든다(`GoogleHttpClients` 연결 풀, `GoogleCircuitBreakers`, `GoogleMonthlyBudget` 월 한도, `GoogleRequestPace` 분당 한도). 실패 해석(4xx·5xx·타임아웃 단계·요청이 나갔는지)은 외부 API 공통 `common/http/ExternalCallFailure`가 맡는다. 클라이언트는 요청을 만들고 응답을 읽기만 한다.
- **월 한도 환불**: Google이 과금하지 않는 실패는 월 한도를 1 돌려준다. 5xx는 Google이 서버 오류를 과금하지 않고([사용량 보고](https://developers.google.com/maps/reporting/gmp-reporting)), 연결 거부·연결 타임아웃·없는 호스트는 요청이 Google에 닿지 않았다. 장소 검색은 쿼터 초과(429)도 돌려주고, Geocoding은 성공한 요청만 과금해 4xx를 모두 돌려준다. 연결한 뒤 응답을 기다리다 끊긴 요청은 Google이 처리했을 수 있어 돌려주지 않는다. 분당 한도는 돌려주지 않는다. 돌려주지 못하면 WARN 로그만 남기고 응답은 그대로 503이다.

## 막힌 지점 기록

사용자가 장소를 찾다 막혀 기록을 남기지 못하고 나가는 곳을 찾고 재현하려고, 장소 검색·선택 한 번마다 `place_lookup_log`에 한 줄을 남긴다(`place/lookuplog`). 앱은 이 실패들을 GA4에 남기지 않아 서버에서 본다. 응답 형식은 바뀌지 않아 앱 출시가 필요 없다.

- **상황**: 회원 ID, 검색어(사용자가 친 글자, 앞뒤 공백을 떼고 100자까지), 고른 장소 ID, 앱 `User-Agent`(255자까지, iOS는 앱 버전이 들어 있다), 같은 검색을 묶는 `session_token`.
- **문제**: `outcome` — 검색은 `RESULTS`·`NO_RESULTS`, 선택은 `REGION_FOUND`·`REGION_NOT_FOUND`(국가는 앎)·`COUNTRY_UNKNOWN`, 둘 다 실패면 `FAILED`와 `error_code`(예: `PLACE_PROVIDER_UNAVAILABLE`).
- **결과**: 검색 후보 수, 찾은 국가 코드와 지역 코드(`region.region_code`).
- Google 약관상 장소는 `place_id`만 남기고 이름·주소·좌표는 남기지 않는다. 주소가 필요하면 아래처럼 장소 ID로 다시 조회한다.
- 회원 한도(429)나 한도 저장소 장애(503)로 컨트롤러에 들어가기 전에 막힌 검색·선택도 `FAILED`로 남긴다. 예상하지 못한 예외(500)는 `error_code=UNEXPECTED`다.
- Google을 부른 뒤 짧게 한 줄만 넣고, 넣지 못해도 WARN(`event=PLACE_LOOKUP_LOG_FAILED`)만 남기고 응답은 그대로 나간다.
- 30일이 지난 줄은 매일 UTC 19:45에 지우고, 파기하는 회원의 줄은 회원 파기 배치가 지운다.
- 앱에서만 거르는 실패(앱 지역 목록에 없는 나라: HK·GU·MO·MP 등)는 서버가 모른다. 대신 그 나라 코드로 `REGION_FOUND`가 남았는데 저장이 없으면 그 경우로 본다.

### 찾는 SQL

지역을 못 찾았거나 실패한 선택 뒤 1시간 안에 기록 저장이 없는 경우(막혀서 나감). 직접 지역을 고르면 장소가 빠질 수 있어 장소 ID가 아니라 회원의 저장 여부로 본다.

```sql
SELECT l.created_at, l.member_id, l.outcome, l.error_code, l.place_id, l.country_code, l.user_agent,
       (SELECT s.query FROM place_lookup_log s
         WHERE s.member_id = l.member_id AND s.session_token = l.session_token AND s.kind = 'SEARCH'
         ORDER BY s.created_at DESC LIMIT 1) AS last_query
FROM place_lookup_log l
WHERE l.kind = 'SELECT' AND l.outcome <> 'REGION_FOUND'
  AND NOT EXISTS (SELECT 1 FROM travel_record r
                   WHERE r.member_id = l.member_id
                     AND r.created_at BETWEEN l.created_at AND l.created_at + INTERVAL 1 HOUR)
ORDER BY l.created_at DESC;
```

장소는 골랐지만(지역을 찾았어도) 1시간 안에 저장이 없는 경우. 서버는 지역을 찾았는데 앱이 거른 나라(HK 등), 고른 뒤 앱 화면이 멈춘 경우, 사진 업로드 한도에 막힌 경우가 여기에 함께 나온다. 이유는 `outcome`·`country_code`와 서버 로그로 나눠 본다.

```sql
SELECT l.created_at, l.member_id, l.outcome, l.place_id, l.country_code, l.region_code, l.user_agent
FROM place_lookup_log l
WHERE l.kind = 'SELECT'
  AND NOT EXISTS (SELECT 1 FROM travel_record r
                   WHERE r.member_id = l.member_id
                     AND r.created_at BETWEEN l.created_at AND l.created_at + INTERVAL 1 HOUR)
ORDER BY l.created_at DESC;
```

검색만 하고 장소를 고르지 않은 검색 세션(후보가 없거나 원하는 장소가 없었음). 검색과 선택은 `session_token`으로 잇는다. 선택에 토큰이 없으면(검색 없이 장소 ID로 다시 고른 경우) 잇지 못해 그 세션이 여기에 잘못 나올 수 있다.

```sql
SELECT s.member_id, s.session_token, MAX(s.created_at) AS last_at, COUNT(*) AS searches,
       SUM(s.outcome = 'NO_RESULTS') AS empty_results, SUM(s.outcome = 'FAILED') AS failures,
       SUBSTRING_INDEX(GROUP_CONCAT(s.query ORDER BY s.created_at DESC SEPARATOR ' | '), ' | ', 3) AS last_queries
FROM place_lookup_log s
WHERE s.kind = 'SEARCH'
  AND NOT EXISTS (SELECT 1 FROM place_lookup_log x
                   WHERE x.kind = 'SELECT' AND x.member_id = s.member_id AND x.session_token = s.session_token)
GROUP BY s.member_id, s.session_token
ORDER BY last_at DESC;
```

주의: 지도 화면의 장소 검색도 같은 API를 쓴다. 지도에서 그 지역으로 이동만 한 경우도 "고르고 저장 안 함"과 "검색만 하고 나감"에 섞여 나온다. 서버는 어느 화면인지 모른다.
게스트로 쓰다 이미 가입한 소셜 계정으로 들어가면(게스트 버림) 저장은 다른 회원 ID로 남아 막힌 것처럼 보인다.

### 재현

- 검색: 같은 검색어로 `GET /api/v1/places/search?query=...&sessionToken=...`를 부른다.
- 선택: 장소 ID로 Place Details(필드 `id,addressComponents,shortFormattedAddress`)를 다시 불러 Google이 준 주소 구성요소를 보고, `KoreanAddressRegionMatcher`가 왜 지역을 못 찾았는지 본다(예: 대천해수욕장은 시·도가 빠져 있었다).

## 확인 범위

Google 실호출에는 유효한 API 키가 필요하다. 단위 테스트는 Google 자동완성·필드 마스크·주소 기반 지역 추천·오류 변환, 검색 응답, 장소 ID 재조회, 기록 저장 및 오류 흐름을 모의 응답으로 검증한다. 서킷 브레이커와 환불 기준은 시계를 돌리는 단위 테스트로, 회원 한도와 Google 월·분당 한도와 환불은 통합 테스트로 검증한다.
