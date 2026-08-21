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
  --locations-source shared/src/commonMain/kotlin/com/mapmory/shared/domain/model/KoreanDistrictCode.kt \
  --resource-output shared/src/commonMain/composeResources/files
```

현재 생성 결과는 17개 시·도, 225개 표시 경계입니다. 2018년 원본에만 존재하는 인천의 과거 구역 4개는 현재 앱의 canonical `Location`과 대응하지 않아 생성에서 제외했습니다. 행정구역 데이터가 갱신되면 새 원본과 `KoreanDistrictCode.kt`를 함께 검토한 뒤 생성해야 합니다.

경계 원본의 이용 조건과 출처 표기는 [원본 저장소 안내](https://github.com/southkorea/southkorea-maps)를 따릅니다. 경계 데이터는 앱 기능을 위한 정적 리소스이고 여행 기록·Location ID 같은 비즈니스 데이터의 소유자가 아닙니다.
