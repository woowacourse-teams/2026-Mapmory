# 지역 경계 데이터

앱은 사용자가 고른 나라 안에서 찍은 사진을 찾을 때, 사진 GPS가 그 나라 경계 안인지 **폰에서** 판정한다. 사진 위치를 서버로 보내지 않기 위해서다. 서버는 판정에 쓸 경계만 `GET /api/v1/regions/{countryCode}/boundary`로 내려준다([API 명세](../../api.md)).

## 왜 서버가 주나

예전에는 앱에 넣은 세계 지도(Natural Earth 1:1억 1천만)를 그리기와 사진 판정에 같이 썼다. 이 지도에는 홍콩·괌·싱가포르 같은 작은 나라·지역 75곳이 없어서 "아직 앱에서 고를 수 없어요"가 떴다. 한 단계 정밀한 1:5천만 지도로 채워도 작은 곳은 모양이 거칠어(바티칸·마카오는 실제 위치와 겹치지 않음) 사진을 놓친다. 정밀한 1:1천만 경계를 앱에 모두 넣으면 지구본이 두 배 무거워진다.

그래서 역할을 나눈다.

| 용도 | 데이터 | 위치 |
| --- | --- | --- |
| 지구본 그리기 | 가벼운 지도 | 앱에 내장 |
| 사진 판정 | 정밀한 경계 | 서버가 나라별로 제공, 앱이 버전과 함께 저장 |

어떤 나라가 있는지와 그 코드(ISO 3166-1 alpha-2)의 원천은 서버 `region` 테이블이다. 테이블에 없는 코드는 경계 파일이 있어도 `404 REGION_NOT_FOUND`다.

## 데이터

- 원본: [Natural Earth](https://www.naturalearthdata.com/) v5.1.2 `ne_10m_admin_0_map_units` (퍼블릭 도메인). map units는 프랑스령 기아나·레위니옹처럼 해외 영토를 본국과 나눠 둔다.
- 서버 나라 249곳 중 248곳의 경계가 있다. 미국령 해외 제도(`UM`)는 원본에 없어 `404 REGION_BOUNDARY_NOT_FOUND`다.
- 바깥 테두리만 남기고, 약 200m(`0.002`도) 허용 오차로 점을 줄인 뒤 소수점 5자리로 반올림했다.
- 파일: `src/main/resources/region-boundaries/{코드}.json`, 버전: `region-boundaries/VERSION`. 합계 약 9MB이고 대부분 1~50KB다. 가장 큰 캐나다가 1.1MB다.
- 서버는 요청된 나라만 읽고 최근 64개 나라를 메모리에 둔다.

## 다시 만들기

`backend/`에서 실행한다. 스크립트는 원본 sha256을 확인하고, `V6__insert_country.sql`의 나라 코드만 만든다.

```bash
curl -L --fail -o /tmp/ne_10m_admin_0_map_units.geojson \
  https://raw.githubusercontent.com/nvkelso/natural-earth-vector/v5.1.2/geojson/ne_10m_admin_0_map_units.geojson
python3 tools/region-boundary/generate_region_boundaries.py /tmp/ne_10m_admin_0_map_units.geojson
```

원본이나 허용 오차를 바꾸면 `VERSION`이 바뀌고, 앱은 다음 요청 때 새 경계로 바꾼다.

## 앞으로

확대하면서 작은 섬과 시·군·구까지 자연스럽게 보이게 하려면 벡터 타일이 맞다. 그때도 나라 목록과 코드의 원천은 `region` 테이블로 둔다.
