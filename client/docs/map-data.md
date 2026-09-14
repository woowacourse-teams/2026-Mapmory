# 지도 경계 데이터

세계 지도는 [Natural Earth Admin 0 Countries 110m](https://www.naturalearthdata.com/downloads/110m-cultural-vectors/110m-admin-0-countries/) 데이터를 사용합니다. 이 축척은 지구본 전체 화면에 맞는 저용량 개요용 데이터이며, Natural Earth의 사실상(de facto) 국가 경계를 따릅니다. 국가 코드는 ISO 3166-1 alpha-2를 사용합니다.

Natural Earth에서 공식 코드가 없는 북키프로스, 코소보, 소말릴란드는 각각 `XC`, `XK`, `XS`라는 지도 전용 두 글자 코드를 사용합니다.

원본 GeoJSON은 앱에 넣지 않습니다. 원본의 각 `Polygon`/`MultiPolygon`에서 외곽 링만 추출하고, 경도·위도를 소수점 4자리로 줄인 뒤 Kotlin 파일 10개로 분할합니다. 현재 생성 데이터는 약 177개 국가/영토를 포함합니다.

재생성 방법:

```bash
curl -L --fail -o /tmp/ne_110m_admin_0_countries.geojson \
  https://raw.githubusercontent.com/nvkelso/natural-earth-vector/master/geojson/ne_110m_admin_0_countries.geojson
python3 tools/map/generate_world_map.py /tmp/ne_110m_admin_0_countries.geojson
```

국가별 여러 섬은 여러 링으로 렌더링합니다. 호수·내부 홀은 생략했기 때문에 지구본 전체 개요에서는 충분하지만, 확대 가능한 정밀 지도에는 더 높은 축척 데이터가 필요합니다.

## 대한민국 시·도 데이터

대한민국 지도 생성기는 [geoBoundaries KOR ADM1](https://www.geoboundaries.org/api/current/gbOpen/KOR/ADM1/)의 2021년 간소화 경계를 기준 입력으로 사용합니다. 17개 시·도와 섬을 포함하며 원본 GeoJSON은 앱에 포함하지 않습니다. 시·도 코드는 ISO 3166-2(`KR-11` 등)를 사용하고, 시·군·구 코드는 행정표준코드(`11680` 등)를 사용합니다.

현재 초기 시·도 개요는 [southkorea/southkorea-maps의 KOSTAT 2018 시·도 경계](https://github.com/southkorea/southkorea-maps/blob/fe65e05e549d04083e52f380a7e9166a8ea0a01e/kostat/2018/json/skorea-provinces-2018-geo.json)를 개발 시점에만 읽고, 17개 시·도 모두의 외곽 링을 RDP tolerance `0.002`도로 단순화해 사용합니다. 서로 맞닿는 시·도가 같은 원본 좌표를 공유해야 울산–경남이나 세종–충남 사이에 틈이 생기지 않으므로, 한 화면에서 geoBoundaries와 KOSTAT 경계를 섞지 않습니다. 이 보정은 초기 개요 geometry에만 적용하며 상세 지도와 서버 Location 코드는 변경하지 않습니다.

```bash
curl -L --fail -o /tmp/geoBoundaries-KOR-ADM1_simplified.geojson \
  https://github.com/wmgeolab/geoBoundaries/raw/9469f09/releaseData/gbOpen/KOR/ADM1/geoBoundaries-KOR-ADM1_simplified.geojson
python3 tools/map/generate_korea_map.py /tmp/geoBoundaries-KOR-ADM1_simplified.geojson
```

앱에 포함되는 topology-compatible 시·도 개요를 재생성하려면 다음을 사용합니다.

```bash
curl -L --fail -o /tmp/skorea-provinces-2018-geo.json \
  https://raw.githubusercontent.com/southkorea/southkorea-maps/fe65e05e549d04083e52f380a7e9166a8ea0a01e/kostat/2018/json/skorea-provinces-2018-geo.json
python3 tools/map/generate_korea_map.py \
  /tmp/geoBoundaries-KOR-ADM1_simplified.geojson \
  --province-override-source /tmp/skorea-provinces-2018-geo.json \
  --province-override-tolerance 0.002
```

RDP tolerance는 경도·위도 단위이며, 낮출수록 형태는 정밀해지고 생성 데이터와 Canvas 렌더링 비용이 커집니다. 현재 값은 시·도 실루엣과 맞닿는 경계를 보존하면서 원본 전체 좌표를 그대로 포함하지 않기 위한 기준값입니다.

## 대한민국 시·군·구 데이터

시·군·구 경계 원본은 개발 시점에만 [southkorea/southkorea-maps의 KOSTAT 2018 데이터](https://github.com/southkorea/southkorea-maps/tree/master/kostat/2018/json)를 사용합니다. 앱 실행 중에는 GitHub Raw나 다른 외부 주소를 호출하지 않습니다.

생성기는 `KoreanDistrictCode.kt`의 앱 코드와 원본의 2018 통계청 코드를 매칭해, 최종 리소스에 행정표준코드만 기록합니다. 예를 들어 원본 평창군 `32340`은 앱 코드 `51760`, 원본 강원도 `32`는 앱 코드 `KR-42`로 변환됩니다. 오래된 코드 prefix를 앱에서 임의 변환하지 않으므로, 지도 클릭 결과와 `Location.regionCode`가 같은 값을 사용합니다.

프로토타입의 표시 단위에 맞춰 광역시·특별시는 구 단위로, 일반 도는 시·군 단위로 생성합니다. `Polygon`과 `MultiPolygon`의 외곽 링만 저장하며, 내부 링은 hole이므로 저장·채우기 대상에서 제외합니다. 좌표는 소수점 5자리로 정규화하고 시·도별 JSON 리소스를 분리해 시·도 선택 뒤 해당 파일 하나만 지연 파싱합니다.

```bash
python3 tools/map/generate_korea_map.py \
  --district-source /tmp/skorea-municipalities.json \
  --district-override-source /tmp/incheon-reorganized-districts.json \
  --locations-source shared/src/commonMain/kotlin/com/mapmory/shared/domain/model/KoreanDistrictCode.kt \
  --district-tolerance 0.0005 \
  --resource-output shared/src/commonMain/composeResources/files
```

2025년 2분기 원본의 인천 행정동 경계를 개편 기준에 맞춰 합치기 위해 먼저 다음 보정 리소스를 생성합니다.

```bash
python3 tools/map/convert_korea_shapefile.py \
  /tmp/bnd_dong_00_2025_2Q.shp \
  --aggregate-incheon-districts \
  --output /tmp/incheon-reorganized-districts.json
```

생성 결과는 17개 시·도, 230개 표시 경계이며, 현재 선택 가능한 지역 모두에 번들 geometry가 있습니다. 원본 시·군·구 데이터에 없는 인천의 제물포구(`28125`), 영종구(`28155`), 서해구(`28275`), 검단구(`28290`)는 2025년 2분기 행정동 경계를 개발 시점에 합쳐 생성합니다. 앱은 생성된 시·도별 JSON만 읽고, 원본 Shapefile이나 외부 네트워크에는 접근하지 않습니다.

## 2026년 행정구역 최신화 감사

### 감사 기준

- 기준일: `2026-09-03`
- 공식 코드 기준: 행정안전부가 공개한 `2026-07-20` 행정구역 코드 스냅샷
- 비교 대상: `KoreanDistrictCode.kt`, `StaticRegionCatalog.kt`, 생성된 시·도·시·군·구 리소스, 지도 생성기
- 앱 표시 범위: 프로토타입과 동일하게 시·도 선택 후 필요한 시·군·구까지
- 비교하지 않은 범위: 읍·면·리와 행정동 단위의 변경, 좌표를 최신 공식 GeoJSON과 대조하는 geometry 정확도

행정안전부 기준으로 2026년 7월 1일부터 광주광역시와 전라남도가 폐지되고 `전남광주통합특별시`로 통합되었습니다. 2026년 7월 20일 공지에서 확인되는 추가 변경은 세종특별자치시의 행정동 `집현동` 신설이며, 법정동·시·군·구 단위의 지도 표시에는 영향을 주지 않습니다.

참고 자료:

- [행정안전부 2026년 7월 1일 행정구역 변경내역](https://www.mois.go.kr/frt/bbs/type001/commonSelectBoardArticle.do?bbsId=BBSMSTR_000000000052&nttId=127039)
- [행정안전부 2026년 7월 20일 행정구역 변경내역](https://www.mois.go.kr/frt/bbs/type001/commonSelectBoardArticle.do?bbsId=BBSMSTR_000000000052&nttId=127979)
- [행정안전부 행정구역 변경내역 게시판](https://www.mois.go.kr/frt/bbs/type001/commonSelectBoardList.do?bbsId=BBSMSTR_000000000052)
- [인천광역시 행정구역 현황](https://www.incheon.go.kr/IC040102)

### 비교 결과

| 항목 | 앱·번들 현재 상태 | 공식 기준과 비교한 결과 | 판정 |
| --- | --- | --- | --- |
| 시·도 수 | 17개. `KR-29 광주광역시`와 `KR-46 전라남도`가 분리됨 | 공식 기준은 16개이며 두 지역은 `전남광주통합특별시`로 통합됨 | 코드·계층 수정 필요 |
| 통합 지역의 하위 지역 | 기존 코드 27개 사용 | 현재 코드 27개로 모두 변경됨 | legacy alias와 canonical 코드 필요 |
| 인천 지역 목록 | 현재 구·군 11개가 `Location`에 존재 | 공식 기준의 2군 9구와 목록은 일치함 | 목록과 경계 리소스가 일치함 |
| 인천 경계 리소스 | 11개 모두 번들됨 | 제물포구·영종구·서해구·검단구는 행정동 경계를 합쳐 생성하고, 미추홀구를 포함해 11개를 제공함 | 지도 선택 가능 |
| 경기도 일반구 | 화성시 등을 시 단위로 표시 | 프로토타입 규칙상 일반 도시는 시·군 단위로 표시 | 현재는 의도된 동작 |
| 읍·면·리·행정동 | 앱의 선택 단위에 포함하지 않음 | 집현동 신설 등은 현재 지도 해상도보다 하위 단위 | 이번 수정 범위에서 제외 |
| 출장소 | 앱의 선택 대상에 포함하지 않음 | 공식 코드에 존재하지만 일반적인 시·군·구 선택 지역이 아님 | 의도적으로 제외 |

앱의 원시 지역 목록은 256개이고, 프로토타입 표시 규칙을 적용한 선택 가능 지역은 230개입니다. 생성된 정적 경계도 230개이며, 테스트에서 선택 가능 지역과 번들 geometry의 코드 집합이 일치하는지 확인합니다.

### 전남광주통합특별시 코드 매핑

아래 표의 왼쪽은 현재 앱에 남아 있는 legacy 코드이고, 오른쪽은 2026년 공식 코드입니다.

| 기존 앱 지역 | 기존 코드 | 현재 지역 | 현재 코드 |
| --- | ---: | --- | ---: |
| 광주광역시 동구 | `29110` | 전남광주통합특별시 동구 | `12210` |
| 광주광역시 서구 | `29140` | 전남광주통합특별시 서구 | `12240` |
| 광주광역시 남구 | `29155` | 전남광주통합특별시 남구 | `12270` |
| 광주광역시 북구 | `29170` | 전남광주통합특별시 북구 | `12300` |
| 광주광역시 광산구 | `29200` | 전남광주통합특별시 광산구 | `12330` |
| 전라남도 목포시 | `46110` | 전남광주통합특별시 목포시 | `12110` |
| 전라남도 여수시 | `46130` | 전남광주통합특별시 여수시 | `12130` |
| 전라남도 순천시 | `46150` | 전남광주통합특별시 순천시 | `12150` |
| 전라남도 나주시 | `46170` | 전남광주통합특별시 나주시 | `12170` |
| 전라남도 광양시 | `46230` | 전남광주통합특별시 광양시 | `12190` |
| 전라남도 담양군 | `46710` | 전남광주통합특별시 담양군 | `12710` |
| 전라남도 곡성군 | `46720` | 전남광주통합특별시 곡성군 | `12720` |
| 전라남도 구례군 | `46730` | 전남광주통합특별시 구례군 | `12730` |
| 전라남도 고흥군 | `46770` | 전남광주통합특별시 고흥군 | `12740` |
| 전라남도 보성군 | `46780` | 전남광주통합특별시 보성군 | `12750` |
| 전라남도 화순군 | `46790` | 전남광주통합특별시 화순군 | `12760` |
| 전라남도 장흥군 | `46800` | 전남광주통합특별시 장흥군 | `12770` |
| 전라남도 강진군 | `46810` | 전남광주통합특별시 강진군 | `12780` |
| 전라남도 해남군 | `46820` | 전남광주통합특별시 해남군 | `12790` |
| 전라남도 영암군 | `46830` | 전남광주통합특별시 영암군 | `12800` |
| 전라남도 무안군 | `46840` | 전남광주통합특별시 무안군 | `12810` |
| 전라남도 함평군 | `46860` | 전남광주통합특별시 함평군 | `12820` |
| 전라남도 영광군 | `46870` | 전남광주통합특별시 영광군 | `12830` |
| 전라남도 장성군 | `46880` | 전남광주통합특별시 장성군 | `12840` |
| 전라남도 완도군 | `46890` | 전남광주통합특별시 완도군 | `12850` |
| 전라남도 진도군 | `46900` | 전남광주통합특별시 진도군 | `12860` |
| 전라남도 신안군 | `46910` | 전남광주통합특별시 신안군 | `12870` |

공식 정부 코드의 통합 시·도 값은 `12000`이지만, 앱은 현재 `KR-11`과 같은 별도 문자열 형식의 시·도 코드를 사용합니다. 따라서 서버와 합의하기 전까지 `KR-12`를 임의로 canonical 값으로 확정하지 않습니다. 새 canonical 값이 결정되면 기존 `KR-29`, `KR-46`과 27개 구·시·군 코드는 기존 여행 기록을 보존하기 위한 alias 또는 migration 대상으로 다룹니다.

### 인천 경계 보정

2025년 2분기 시·군·구 원본에는 2026년 7월 신설된 다음 4개 구의 geometry가 별도 항목으로 존재하지 않습니다.

```text
28125 제물포구
28155 영종구
28275 서해구
28290 검단구
```

행정동 원본의 외곽 링을 공식 개편 구역에 맞춰 합친 뒤, 생성 단계에서 canonical 코드와 `provinceCode=KR-28`을 부여해 정적 리소스로 저장합니다. 기존 중구·동구·서구 원본은 앱의 선택 단위와 대응하지 않으므로 그대로 노출하지 않고, 새 4개 구의 geometry로 대체합니다. `미추홀구`는 시·군·구 원본에 포함되어 별도 집계가 필요하지 않습니다.

### 생성기와 테스트의 현재 한계

- 생성기의 `skipped` 결과가 출력만 되고 생성 실패로 이어지지 않아, 지역이 조용히 빠질 수 있습니다.
- 현재 생성기는 원본 코드와 앱 목록을 생성 시점에 매칭합니다. 최신 코드로 갱신할 때는 원본 경계와 `KoreanDistrictCode.kt`를 함께 갱신해야 합니다.
- [PhotoRecommendationRegionResourceAssertions.kt](../shared/src/commonTest/kotlin/com/mapmory/shared/presentation/photo/PhotoRecommendationRegionResourceAssertions.kt)는 선택 가능한 모든 지역에 geometry가 있는지 검사합니다.
- [GeneratedKoreaDistrictMapData.kt](../shared/src/commonMain/kotlin/com/mapmory/shared/presentation/map/data/GeneratedKoreaDistrictMapData.kt)는 기존 17개 시·도 코드만 지원하므로 통합 시·도는 별도 리소스와 로딩 규칙이 필요합니다.

### 수정 순서

1. 백엔드와 `전남광주통합특별시`의 canonical 시·도 코드와 기존 코드 호환 정책을 합의한다.
2. 2026년 기준으로 관리되는 경계 원본을 선정하고, 버전·출처·checksum을 기록한 뒤 정적 리소스를 재생성한다.
3. 인천 4개 개편 구와 통합 시·도의 경계를 추가하고, 지도 선택 결과가 `Location.regionCode`와 일치하는지 검증한다.
4. 생성기의 누락 지역을 오류로 승격하고, 선택 가능 지역과 번들 경계의 코드 집합이 정확히 일치하는 테스트를 추가한다.
5. 기존 여행 기록이 새 코드로 사라지지 않는지 migration·alias·지도 라우팅 테스트를 추가한다.

이번 감사는 코드·명칭·지역 목록·번들 리소스의 일치 여부를 확인한 결과입니다. 실제 polygon 모양이 2026년 공식 경계와 같은지 확인하려면 최신 공식 GeoJSON 원본을 선정한 뒤 좌표 단위 비교를 별도로 수행해야 합니다.

경계 원본의 이용 조건과 출처 표기는 [원본 저장소 안내](https://github.com/southkorea/southkorea-maps)를 따릅니다. 경계 데이터는 앱 기능을 위한 정적 리소스이고 여행 기록·Location ID 같은 비즈니스 데이터의 소유자가 아닙니다.
