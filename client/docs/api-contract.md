# Mapmory Client API Contract

## 기본 정보

| 항목 | 값 |
| --- | --- |
| Base URL | `/api/v1` |
| 데이터 형식 | `application/json` |
| 문자 인코딩 | UTF-8 |
| 임시 사용자 식별 | `X-Member-Id` 요청 헤더 |
| 인증 | 추후 Access Token으로 교체 |

인증 도입 전에는 사용자 전용 요청에 `X-Member-Id`를 사용한다. 인증 도입 후에는
헤더를 제거하고 Access Token에서 회원 ID를 조회한다. 여행 기록은 작성자 본인만
접근할 수 있으며, 다른 회원의 기록 ID로 접근해도 `404 TRAVEL_RECORD_NOT_FOUND`를
반환한다.

## 공통 응답

성공 응답은 `data`로 감싼다.

```json
{ "data": {} }
```

오류 응답은 다음 형식을 사용한다.

```json
{
  "code": "TRAVEL_RECORD_NOT_FOUND",
  "message": "여행 기록을 찾을 수 없습니다.",
  "fieldErrors": []
}
```

유효성 오류의 `fieldErrors`는 다음 형식이다.

```json
[
  { "field": "title", "reason": "제목은 필수입니다." }
]
```

주요 상태 코드는 `200`, `201`, `204`, `400`, `401`, `403`, `404`, `409`, `500`이다.

목록 API는 0부터 시작하는 페이지 번호를 사용한다. `page` 기본값은 `0`,
`size` 기본값은 `20`, 최대값은 `100`이다.

```json
{
  "data": {
    "items": [],
    "page": 0,
    "size": 20,
    "totalElements": 0,
    "totalPages": 0,
    "hasNext": false
  }
}
```

## 국가 및 지역 API

### 국가 목록

`GET /api/v1/countries`

```json
{
  "data": [
    { "id": 1, "code": "KR", "name": "대한민국" }
  ]
}
```

### 지역 목록

`GET /api/v1/locations`

| Query | 필수 | 설명 |
| --- | --- | --- |
| `countryId` | 조건부 | 국가의 시·도 조회 시 필요 |
| `parentId` | 아니요 | 상위 지역의 직속 하위 지역 조회 |
| `keyword` | 아니요 | 지역명 또는 지역 코드 검색 |

`countryId`만 전달하면 시·도를 반환하고, `parentId`를 전달하면 해당 시·도의
시·군·구를 반환한다.

```json
{
  "data": [
    {
      "id": 1,
      "countryId": 1,
      "parentId": null,
      "regionCode": "11",
      "name": "서울특별시",
      "locationType": "PROVINCE"
    }
  ]
}
```

### 지역 상세

`GET /api/v1/locations/{locationId}`

응답은 지역 객체를 `data`로 감싸며, 없으면 `404 LOCATION_NOT_FOUND`를 반환한다.

## 이미지 업로드 API

### Presigned URL 발급

`POST /api/v1/uploads/presigned-urls`

헤더: `X-Member-Id` 필수

```json
{
  "files": [
    {
      "fileName": "seoul-trip.jpg",
      "contentType": "image/jpeg",
      "fileSize": 3145728
    }
  ]
}
```

서버는 UUID 기반의 `objectKey`를 생성한다. 클라이언트 파일명을 Object Key로
직접 사용하지 않는다.

```text
travel-records/{memberId}/{uuid}.{extension}
```

```json
{
  "data": {
    "uploads": [
      {
        "objectKey": "travel-records/10/550e8400-e29b-41d4-a716-446655440000.jpg",
        "presignedUrl": "https://...",
        "method": "PUT",
        "contentType": "image/jpeg",
        "expiresIn": 300
      }
    ]
  }
}
```

클라이언트는 발급받은 URL로 S3에 직접 `PUT`하고, 여행 기록 요청에는
업로드가 완료된 `objectKeys`만 전달한다. Presigned URL은 DB에 저장하지 않는다.

오류 코드는 `INVALID_FILE_TYPE`, `FILE_SIZE_EXCEEDED`, `TOO_MANY_FILES`,
`MEMBER_NOT_FOUND`를 사용한다.

## 여행 기록 API

여행 기록의 `locationId`는 최종 선택 단계인 `DISTRICT` 지역 ID여야 한다.
날짜를 입력하지 않으면 두 값 모두 `null`이다. 시작일만 입력하면 서버가 종료일을
시작일과 같게 저장한다. 종료일만 입력하거나 종료일이 시작일보다 빠르면
`INVALID_TRAVEL_DATE_RANGE`를 반환한다.

`title`과 `content`는 요청에 포함되어야 하지만 빈 문자열과 공백 문자열을 허용한다.

### 생성

`POST /api/v1/travel-records`

헤더: `X-Member-Id` 필수

```json
{
  "locationId": 1,
  "title": "비 오는 날의 종로",
  "content": "골목을 걸으며 오래된 가게들을 기록했다.",
  "startDate": null,
  "endDate": null,
  "objectKeys": ["travel-records/10/example.jpg"]
}
```

응답은 `201 Created`이며 `Location` 헤더와 함께 다음을 반환한다.

```json
{ "data": { "id": 101 } }
```

### 목록

`GET /api/v1/travel-records?locationId={id}&keyword={keyword}&page={page}&size={size}`

헤더: `X-Member-Id` 필수. 현재 회원의 기록만 반환하고 기본 정렬은 `id DESC`다.

목록 항목은 `id`, `member(id, name)`, `location(id, countryCode, regionCode, name)`,
`title`, `startDate`, `endDate`, `thumbnailUrl`, `thumbnailUrlExpiresIn`,
`createdAt`, `updatedAt`을 포함한다.

### 상세

`GET /api/v1/travel-records/{travelRecordId}`

헤더: `X-Member-Id` 필수. 상세 항목은 목록 항목에 `content`와 `media`를 추가한다.

미디어 항목은 `id`, `objectKey`, `viewUrl`, `viewUrlExpiresIn`, `sortOrder`를 포함한다.
조회용 URL은 서버가 Presigned GET URL로 생성한다.

### 수정

`PUT /api/v1/travel-records/{travelRecordId}`

생성과 같은 본문을 사용한다. `objectKeys`는 전체 교체 방식이며 배열 순서를
`sortOrder`로 저장한다. 성공 응답은 `200 OK`와 `{ "data": { "id": 101 } }`다.

### 삭제

`DELETE /api/v1/travel-records/{travelRecordId}`

헤더: `X-Member-Id` 필수. 성공 시 `204 No Content`를 반환한다.

## 여행 통계 API

### 내 전체 여행 통계 조회

`GET /api/v1/travel-records/statistics`

현재 회원이 작성한 전체 기간의 여행 기록과 연결된 미디어를 실시간 집계한다.

```json
{
  "data": {
    "recordCount": 24,
    "mediaCount": 138,
    "visitedCountryCount": 3,
    "visitedKoreaDistrictCount": 8,
    "visitedCountryCodes": ["JP", "KR", "US"],
    "topRegions": [
      {
        "regionId": 10,
        "code": "11",
        "regionType": "PROVINCE",
        "name": "서울특별시",
        "recordCount": 7
      }
    ]
  }
}
```

| 필드 | 설명 |
| --- | --- |
| `recordCount` | 현재 회원의 전체 여행 기록 수 |
| `mediaCount` | 여행 기록에 연결된 전체 미디어 수 |
| `visitedCountryCount` | 기록이 하나 이상 있는 고유 국가 수 |
| `visitedKoreaDistrictCount` | 기록이 하나 이상 있는 대한민국 고유 시·군·구 수 |
| `visitedCountryCodes` | 방문 국가 ISO 코드의 오름차순 목록 |
| `topRegions` | `recordCount DESC`, `regionId ASC` 순서의 상위 3개 지역 |

대한민국 시·군·구 기록은 직속 시·도로 올려 합산하고, 해외 기록은 국가로 합산한다.
기록이 없으면 숫자 필드는 `0`, 목록 필드는 빈 배열을 반환한다.

## 오류 코드

클라이언트에서 우선 처리할 오류 코드는 다음과 같다.

- `VALIDATION_ERROR`
- `INVALID_TRAVEL_DATE_RANGE`
- `TRAVEL_RECORD_NOT_FOUND`
- `LOCATION_NOT_FOUND`
- `INVALID_LOCATION_TYPE`
- `INVALID_OBJECT_KEY`
- `OBJECT_NOT_UPLOADED`
- `MEMBER_NOT_FOUND`

## API 요약

| Method | Endpoint | 설명 |
| --- | --- | --- |
| GET | `/api/v1/countries` | 국가 목록 |
| GET | `/api/v1/locations` | 지역 목록 |
| GET | `/api/v1/locations/{locationId}` | 지역 상세 |
| POST | `/api/v1/uploads/presigned-urls` | 이미지 업로드 URL 발급 |
| POST | `/api/v1/travel-records` | 여행 기록 생성 |
| GET | `/api/v1/travel-records` | 내 여행 기록 목록 |
| GET | `/api/v1/travel-records/{travelRecordId}` | 내 여행 기록 상세 |
| PUT | `/api/v1/travel-records/{travelRecordId}` | 여행 기록 전체 수정 |
| DELETE | `/api/v1/travel-records/{travelRecordId}` | 여행 기록 삭제 |
| GET | `/api/v1/travel-records/statistics` | 내 전체 여행 통계 조회 |

## 아직 결정할 운영 정책

1. 허용 이미지 MIME 타입, 파일당 최대 크기, 기록당 최대 개수
2. 기록 수정·삭제 시 제거된 S3 객체의 삭제 시점
3. 대한민국 행정구역 코드 및 GeoJSON 출처와 갱신 방식

## 아직 명세에 없는 기능

아래 기능은 현재 제공된 최신 API 문서에 정의되어 있지 않으므로 클라이언트에서
임의로 구현하지 않는다.

- 지도 방문 지역 전용 API: `GET /travel-records/map` 추가 여부와 응답 형식

## 지도 데이터 경계

지도 SDK는 지도 타일·스타일·지도 피처를 표시하는 역할만 담당한다. 지도 SDK가
Mapmory의 `Location.regionCode`를 제공한다고 가정하지 않는다.

- 방문 지역 데이터는 Mapmory 서버가 제공한다.
- 지도 색칠은 서버의 `regionCode`와 클라이언트 행정구역 GeoJSON 속성을 매칭한다.
- 지도 SDK 내부 ID나 지도 피처 속성을 Mapmory 지역 코드로 저장하지 않는다.
- 지도 SDK 검색 장소나 정확한 좌표를 여행 기록에 저장하지 않는다.
