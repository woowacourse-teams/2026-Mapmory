# 지도 경계 데이터

세계 지도는 [Natural Earth Admin 0 Countries 110m](https://www.naturalearthdata.com/downloads/110m-cultural-vectors/110m-admin-0-countries/) 데이터를 사용합니다. 이 축척은 지구본 전체 화면에 맞는 저용량 개요용 데이터이며, Natural Earth의 사실상(de facto) 국가 경계를 따릅니다.

원본 GeoJSON은 앱에 넣지 않습니다. 원본의 각 `Polygon`/`MultiPolygon`에서 외곽 링만 추출하고, 경도·위도를 소수점 4자리로 줄인 뒤 Kotlin 파일 10개로 분할합니다. 현재 생성 데이터는 약 177개 국가/영토를 포함합니다.

재생성 방법:

```bash
curl -L --fail -o /tmp/ne_110m_admin_0_countries.geojson \
  https://raw.githubusercontent.com/nvkelso/natural-earth-vector/master/geojson/ne_110m_admin_0_countries.geojson
python3 tools/map/generate_world_map.py /tmp/ne_110m_admin_0_countries.geojson
```

국가별 여러 섬은 여러 링으로 렌더링합니다. 호수·내부 홀은 생략했기 때문에 지구본 전체 개요에서는 충분하지만, 확대 가능한 정밀 지도에는 더 높은 축척 데이터가 필요합니다.

## 대한민국 시·도 데이터

대한민국 지도는 [geoBoundaries KOR ADM1](https://www.geoboundaries.org/api/current/gbOpen/KOR/ADM1/)의 2021년 간소화 경계를 사용합니다. 17개 시·도와 섬을 포함하며 원본 GeoJSON은 앱에 포함하지 않습니다.

```bash
curl -L --fail -o /tmp/geoBoundaries-KOR-ADM1_simplified.geojson \
  https://github.com/wmgeolab/geoBoundaries/raw/9469f09/releaseData/gbOpen/KOR/ADM1/geoBoundaries-KOR-ADM1_simplified.geojson
python3 tools/map/generate_korea_map.py /tmp/geoBoundaries-KOR-ADM1_simplified.geojson
```
