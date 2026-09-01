# Mapmory API 명세

> 기준일: 2026-08-31 · 범위: 인증, 지역 선택, 지도 마킹, 여행 기록, 여행 통계, 이미지 첨부, 사용자 생성 태그

이 문서는 Mapmory API의 기준 계약이다. API 목록의 `구현 전 설계` 항목은 구현에 앞서 합의한 목표 계약이며, 구현이 끝나면 `구현됨`으로 상태를 변경한다.

## 1. 기본 정보

| 항목 | 값 |
| --- | --- |
| Base URL | `/api/v1` |
| 요청·응답 형식 | `application/json` |
| 오류 응답 형식 | `application/problem+json` |
| 날짜 형식 | `YYYY-MM-DD` |
| 인증 | `Authorization: Bearer {accessToken}` |
| 페이지네이션 | `page` 기본 0, `size` 기본 20·최대 100 |

`/auth/**`와 `/health`를 제외한 API는 유효한 Access Token이 필요하다. `/auth/login/kakao`는 인증 없이 호출하지만, 게스트가 계정을 연결할 때는 게스트 Access Token을 함께 보낸다. 여행 기록과 태그는 비공개이며 소유자 본인만 접근할 수 있다. 다른 회원의 리소스 ID를 요청해도 존재 여부를 숨기기 위해 `404`를 반환한다.

### API 목록

| 상태 | Method | Endpoint | 설명 |
| --- | --- | --- | --- |
| 구현됨 | `POST` | `/auth/login/kakao` | 카카오 로그인 |
| 구현됨 | `POST` | `/auth/login/guest` | 게스트 로그인 |
| 구현됨 | `POST` | `/auth/token/refresh` | Access·Refresh Token 회전 재발급 |
| 구현됨 | `POST` | `/auth/logout` | Refresh Token 폐기 |
| 구현됨 | `POST` | `/uploads/presigned-urls` | 이미지 업로드용 Presigned URL 발급 |
| 구현됨 | `POST` | `/travel-records` | 여행 기록 생성 |
| 구현됨 | `GET` | `/travel-records` | 내 여행 기록 목록 조회 |
| 구현됨 | `GET` | `/travel-records/{travelRecordId}` | 내 여행 기록 상세 조회 |
| 구현됨 | `PUT` | `/travel-records/{travelRecordId}` | 내 여행 기록 전체 수정 |
| 구현됨 | `DELETE` | `/travel-records/{travelRecordId}` | 내 여행 기록 삭제 |
| 구현됨 | `GET` | `/travel-records/statistics` | 내 전체 여행 통계 조회 |
| 구현됨 | `GET` | `/travel-records/map-summary/regions/roots` | 루트 Region별 지도 색칠 정보 조회 |
| 구현됨 | `GET` | `/travel-records/map-summary/regions/{regionId}/children` | 직속 하위 Region별 지도 색칠 정보 조회 |
| 구현 전 설계 | `POST` | `/tags` | 내 태그 생성 |
| 구현 전 설계 | `GET` | `/tags` | 내 태그 목록 조회 |
| 구현 전 설계 | `PATCH` | `/tags/{tagId}` | 내 태그 이름 수정 |
| 구현 전 설계 | `DELETE` | `/tags/{tagId}` | 내 태그 삭제 |

### 공통 응답

성공 응답은 `data`로 감싼다.

```json
{
  "data": {}
}
```

오류 응답은 RFC 9457 Problem Details를 사용한다.

```json
{
  "title": "요청 값이 올바르지 않습니다.",
  "status": 400,
  "detail": "1개의 필드가 유효하지 않습니다.",
  "instance": "/api/v1/travel-records",
  "code": "VALIDATION_ERROR",
  "errors": [
    {
      "field": "countryCode",
      "detail": "대문자 2자의 ISO-2 형식이어야 합니다."
    }
  ]
}
```

| 상태 | 의미 |
| --- | --- |
| `200 OK` | 조회·수정 성공 |
| `201 Created` | 생성 성공 |
| `204 No Content` | 삭제 성공 |
| `400 Bad Request` | 형식·유효성·업무 규칙 오류 |
| `401 Unauthorized` | 인증 실패 또는 토큰 만료 |
| `403 Forbidden` | 인증됐지만 접근 권한 없음 |
| `404 Not Found` | 리소스 없음 또는 타인 기록 |
| `409 Conflict` | 중복 또는 리소스 상태 충돌 |
| `429 Too Many Requests` | 요청 한도 초과 |

## 2. Auth API

로그인 방식은 두 가지다. 어느 쪽으로 로그인하든 **발급되는 토큰과 이후 사용 방법은 동일하다.**

| 방식 | 용도 | 요청 본문 |
| --- | --- | --- |
| 카카오 로그인 | 계정을 연결한 사용자 | 카카오 Access Token |
| 게스트 로그인 | 로그인 없이 먼저 사용해보는 사용자 | 없음 |

게스트로 사용하다가 카카오 로그인을 하면 **같은 회원으로 승격**되어 그동안 남긴 기록이 그대로 이어진다.
자세한 조건은 아래 카카오 로그인을 참고한다.

### 토큰 정책

로그인 응답으로 Access Token과 Refresh Token을 함께 받는다. 클라이언트는 둘 다 안전하게 저장한다.

| 토큰 | 만료 | 비고 |
| --- | --- | --- |
| Access Token | 30분 | 보호 API 호출에 사용. 서버에 저장하지 않는다 |
| Refresh Token (회원) | 14일 | 재발급 시 회전한다 |
| Refresh Token (게스트) | 365일 | 게스트는 재로그인 수단이 없어 만료 시 복구가 불가능하다 |

Refresh Token은 **재발급할 때마다 만료가 갱신된다.** 따라서 실제로 만료되는 경우는 그 기간 동안
앱을 한 번도 열지 않은 경우다. 앱 실행 시 재발급을 한 번 호출해두면 세션이 계속 유지된다.

재발급에 성공하면 이전 Refresh Token은 즉시 폐기된다. **응답으로 받은 새 Access·Refresh Token을
모두 저장해야 한다.** 이미 사용한 Refresh Token이 다시 오면 탈취로 간주해 해당 회원의 Refresh Token을
전부 폐기하므로, 재발급 요청이 동시에 두 번 나가지 않도록 해야 한다.

보호 API에는 다음 헤더를 전달한다.

```http
Authorization: Bearer {accessToken}
```

### 카카오 로그인

`POST /api/v1/auth/login/kakao`

앱이 카카오 SDK로 받은 Access Token을 전달하면, 서버가 카카오에 검증한 뒤 Mapmory 토큰을 발급한다.

```json
{
  "kakaoAccessToken": "kakao-access-token"
}
```

게스트로 사용 중이었다면 **게스트 Access Token을 헤더로 함께 보낸다.** 이 경우에만 승격이 일어난다.

```http
Authorization: Bearer {게스트 accessToken}
```

결과는 세 가지다.

| 상황 | 결과 | `isNewMember` |
| --- | --- | --- |
| 게스트 토큰 있음 · 처음 보는 카카오 계정 | **승격**. 게스트로 남긴 기록이 이어진다 | `false` |
| 게스트 토큰 있음 · 이미 가입한 카카오 계정 | 기존 회원으로 로그인. **게스트 기록은 이어지지 않는다** | `false` |
| 게스트 토큰 없음 · 처음 보는 카카오 계정 | 신규 가입 | `true` |
| 게스트 토큰 없음 · 이미 가입한 카카오 계정 | 기존 회원으로 로그인 | `false` |

두 번째 경우 게스트의 Refresh Token은 폐기되어 그 세션으로는 더 이상 갱신할 수 없다.
응답만으로는 승격인지 기존 로그인인지 구분할 수 없으므로, **클라이언트는 카카오 로그인 전에
기록이 이어지지 않을 수 있음을 안내해야 한다.**

#### Response `200 OK`

```json
{
  "data": {
    "accessToken": "mapmory-access-token",
    "refreshToken": "mapmory-refresh-token",
    "isNewMember": true
  }
}
```

### 게스트 로그인

`POST /api/v1/auth/login/guest`

로그인하지 않고 서비스를 사용하기 위한 회원을 만든다. **요청 본문이 없다.**
식별자는 서버가 발급하므로 클라이언트가 보낼 값은 없다.

호출할 때마다 새 회원이 생성된다. 클라이언트는 발급받은 토큰을 저장해 계속 사용해야 하며,
앱을 실행할 때마다 다시 호출하면 안 된다.

응답 형식은 카카오 로그인과 같고 `isNewMember`는 항상 `true`다.

#### Response `200 OK`

```json
{
  "data": {
    "accessToken": "mapmory-access-token",
    "refreshToken": "mapmory-refresh-token",
    "isNewMember": true
  }
}
```

인증 없이 호출되는 경로이므로 같은 출처에서 반복 호출하면 `429 GUEST_LOGIN_RATE_LIMITED`로 거절한다.
정상 사용자가 걸리지 않을 만큼 한도를 넉넉히 두므로, 토큰을 저장해 재사용하면 마주칠 일이 없다.

게스트의 기록은 **그 기기에만 묶여 있다.** 앱을 지우거나 기기를 바꾸면 복구할 수 없으므로,
계정 연결을 유도하는 안내가 필요하다.

### 토큰 재발급

`POST /api/v1/auth/token/refresh`

새 Access·Refresh Token 쌍을 반환한다. 회전 규칙은 위 토큰 정책을 참고한다.

```json
{
  "refreshToken": "mapmory-refresh-token"
}
```

#### Response `200 OK`

```json
{
  "data": {
    "accessToken": "new-mapmory-access-token",
    "refreshToken": "new-mapmory-refresh-token"
  }
}
```

### 로그아웃

`POST /api/v1/auth/logout`

```json
{
  "refreshToken": "mapmory-refresh-token"
}
```

#### Response `204 No Content`

### Auth 오류

| 상태 | `code` | 조건 |
| --- | --- | --- |
| `401` | `INVALID_ACCESS_TOKEN` | Access Token이 유효하지 않음 |
| `401` | `EXPIRED_ACCESS_TOKEN` | Access Token이 만료됨 |
| `401` | `INVALID_KAKAO_TOKEN` | 카카오 Access Token이 유효하지 않음 |
| `401` | `INVALID_REFRESH_TOKEN` | Refresh Token이 유효하지 않거나 폐기됨 |
| `401` | `EXPIRED_REFRESH_TOKEN` | Refresh Token이 만료됨 |
| `403` | `ACCESS_DENIED` | 리소스 접근 권한 없음 |
| `429` | `GUEST_LOGIN_RATE_LIMITED` | 게스트 로그인 요청이 한도를 초과함 |
| `503` | `KAKAO_UNAVAILABLE` | 카카오 인증 서버를 일시적으로 사용할 수 없음 |

## 3. Upload API

이미지는 API 서버를 거치지 않고 S3에 직접 업로드한다. 서버는 UUID 기반 `objectKey`만 발급하며, DB에는 만료되는 Presigned URL이 아닌 `objectKey`를 저장한다.

`objectKey`의 형식은 서버가 정하며 환경에 따라 접두사가 달라질 수 있다. 클라이언트는 값을 해석하지 말고 받은 그대로 저장하고 전달한다.

### 이미지 업로드 URL 발급

`POST /api/v1/uploads/presigned-urls`

#### Request Body

```json
{
  "files": [
    {
      "fileName": "jeju-trip.jpg",
      "contentType": "image/jpeg",
      "fileSize": 3145728
    }
  ]
}
```

| 필드 | 타입 | 필수 | 설명 |
| --- | --- | --- | --- |
| `files` | Object[] | 예 | 1개 이상 |
| `files[].fileName` | String | 예 | 로그·확장자 확인용 원본 파일명 |
| `files[].contentType` | String | 예 | 허용 이미지 MIME 타입 |
| `files[].fileSize` | Long | 예 | 바이트 단위 파일 크기 |

#### Response `200 OK`

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

업로드 후 24시간 안에 여행 기록과 연결되지 않은 객체는 고아 객체로 정리한다.

## 4. Tag API — 구현 전 설계

태그는 회원이 직접 만드는 비공개 리소스다. `연인`, `친구`, `가족`, `라멘맛집`처럼 자유롭게 만들 수 있으며 하나의 여행 기록에 여러 태그를 연결할 수 있다.

`#`은 UI 표현이므로 태그 이름에 저장하지 않는다. 서버는 이름의 앞뒤 공백과 연속 공백을 정리하고 Unicode NFC·소문자를 적용한 `normalizedName`을 중복 판단에 사용한다.

- 이름: 정규화 후 1~30자
- 회원당 최대 10개
- 기록당 최대 5개
- 같은 회원은 정규화 이름이 같은 태그를 중복 생성할 수 없음
- 다른 회원은 같은 이름의 태그를 만들 수 있음

회원당 10개와 기록당 5개는 MVP 사용성을 검증하기 위한 임시 제한이다. 사용자 테스트와 실제 태그 사용 분포를 확인한 뒤 유지·완화 여부를 다시 결정한다.

### 태그 생성

`POST /api/v1/tags`

```json
{
  "name": "라멘맛집"
}
```

#### Response `201 Created`

```json
{
  "data": {
    "id": 12,
    "name": "라멘맛집",
    "createdAt": "2026-08-20T15:30:00",
    "updatedAt": "2026-08-20T15:30:00"
  }
}
```

### 내 태그 목록 조회

`GET /api/v1/tags`

태그는 `createdAt ASC, id ASC`로 정렬한다. 연결된 기록이 없는 태그도 목록에 반환한다. 임시 회원당 제한이 10개이므로 MVP에서는 페이지네이션하지 않는다.

#### Response `200 OK`

```json
{
  "data": [
    {
      "id": 9,
      "name": "연인",
      "createdAt": "2026-08-19T12:00:00",
      "updatedAt": "2026-08-19T12:00:00"
    },
    {
      "id": 12,
      "name": "라멘맛집",
      "createdAt": "2026-08-20T15:30:00",
      "updatedAt": "2026-08-20T15:30:00"
    }
  ]
}
```

### 태그 이름 수정

`PATCH /api/v1/tags/{tagId}`

```json
{
  "name": "서울 라멘맛집"
}
```

성공 시 태그 생성 응답과 같은 객체를 `200 OK`로 반환한다. 이름 변경은 연결된 모든 여행 기록에 즉시 반영된다.

### 태그 삭제

`DELETE /api/v1/tags/{tagId}`

태그와 여행 기록의 연결만 삭제하며 여행 기록 자체는 유지한다.

#### Response `204 No Content`

### Tag 오류

| 상태 | `code` | 조건 |
| --- | --- | --- |
| `400` | `VALIDATION_ERROR` | 이름 형식 또는 길이가 유효하지 않음 |
| `404` | `TAG_NOT_FOUND` | 태그가 없거나 현재 회원의 태그가 아님 |
| `409` | `TAG_NAME_CONFLICT` | 현재 회원에게 같은 정규화 이름의 태그가 있음 |
| `409` | `TAG_LIMIT_EXCEEDED` | 임시 회원당 태그 10개 제한 초과 |

## 5. Travel Record API

기존 여행 기록 엔드포인트는 구현돼 있다. 이 절의 `tagIds`, `tags`, `tagId` 계약은 태그 기능 구현 전 설계다.

### 지역 선택 규칙

클라이언트는 Region의 DB ID가 아닌 표준 코드 경로를 전달한다. 서버는 `parent_id` 관계로 경로를 검증한 뒤 최종 Region의 ID만 `travel_record.region_id`에 저장한다.

| 기록 대상 | 요청 값 | 저장되는 Region |
| --- | --- | --- |
| 해외 국가 | `countryCode` | `COUNTRY` |
| 대한민국 시·군·구 | `countryCode`, `provinceCode`, `districtCode` | `DISTRICT` |

- `countryCode = KR`이면 `provinceCode`, `districtCode`가 모두 필수다.
- `countryCode`는 대문자 2자리이며, 하위 지역 코드는 입력하는 경우 공백 없이 20자 이하여야 한다.
- `provinceCode`는 선택 국가의 직접 자식 `PROVINCE`여야 한다.
- `districtCode`는 선택 시도의 직접 자식 `DISTRICT`여야 한다.
- 해외에서는 MVP 기준 국가 단위만 허용한다.
- 코드 접두사로 부모 지역을 추론하지 않는다.

### 여행 기록 생성

`POST /api/v1/travel-records`

#### Request Body — 대한민국 기록

```json
{
  "countryCode": "KR",
  "provinceCode": "49",
  "districtCode": "50110",
  "title": "비 오는 날의 제주시",
  "content": "골목을 걸으며 오래된 가게들을 기록했다.",
  "startDate": "2026-08-11",
  "endDate": null,
  "objectKeys": [
    "travel-records/10/550e8400-e29b-41d4-a716-446655440000.jpg"
  ],
  "tagIds": [9, 12]
}
```

#### Request Body — 해외 국가 기록

```json
{
  "countryCode": "JP",
  "provinceCode": null,
  "districtCode": null,
  "title": "일본 여행",
  "content": "",
  "startDate": "2026-08-11",
  "endDate": null,
  "objectKeys": [],
  "tagIds": [8]
}
```

| 필드 | 타입 | 필수 | 제약조건 |
| --- | --- | --- | --- |
| `countryCode` | String | 예 | 대문자 2자리이며 존재하는 ISO 3166-1 alpha-2 코드 |
| `provinceCode` | String | 조건부 | `KR`이면 필수, 입력 시 공백 없이 최대 20자 |
| `districtCode` | String | 조건부 | `KR`이면 필수, 입력 시 공백 없이 최대 20자 |
| `title` | String | 예 | 공백이 아닌 문자를 포함해야 하며 최대 200자 |
| `content` | String | 아니요 | `null`, 빈 문자열·공백 허용 |
| `startDate` | LocalDate | 예 | `YYYY-MM-DD`, 오늘 또는 과거 |
| `endDate` | LocalDate | 아니요 | 오늘 또는 과거이며 시작일과 같거나 이후 |
| `objectKeys` | String[] | 아니요 | 업로드 완료된 Object Key 목록 |
| `tagIds` | Long[] | 아니요 | 빈 배열 허용, 최대 5개, 중복 불가, 모두 현재 회원 소유 |

`objectKeys`는 배열 순서대로 `record_media.sort_order`를 0부터 부여해 저장한다. 값이 없거나 `null`이면 미디어를 생성하지 않는다.

`content`가 없거나 `null`이면 서버는 빈 문자열로 정규화해 저장한다.

여행 날짜는 `Asia/Seoul`의 오늘을 기준으로 검증한다. 시작일과 종료일 모두 미래일 수 없으며 오늘은 허용한다. 종료일을 입력했다면 시작일보다 빠를 수 없다. 생성과 수정에 같은 규칙을 적용한다.

`tagIds`가 없거나 `null`이면 태그를 연결하지 않는다. 새 태그를 입력한 클라이언트는 먼저 `POST /tags`로 태그를 생성한 뒤 반환된 ID를 여행 기록 요청에 포함한다. 여행 기록과 태그 연결은 같은 트랜잭션에서 저장한다.

#### Response `201 Created`

```json
{
  "data": {
    "id": 101
  }
}
```

| 상태 | `code` | 조건 |
| --- | --- | --- |
| `400` | `REGION_REQUIRED` | 한국 기록에 시도 또는 시군구가 없음 |
| `404` | `REGION_NOT_FOUND` | 요청한 국가·시도·시군구가 존재하지 않음 |
| `400` | `INVALID_REGION_HIERARCHY` | 요청 지역의 부모 관계가 맞지 않음 |
| `400` | `INVALID_REGION_TYPE` | 대한민국이 시군구 단위가 아니거나 해외 요청에 하위 지역이 포함됨 |
| `400` | `INVALID_TRAVEL_DATE_RANGE` | 시작일·종료일이 미래이거나 종료일이 시작일보다 빠름 |
| `400` | `INVALID_OBJECT_KEY` | Object Key 형식 또는 소유자가 올바르지 않음 |
| `409` | `OBJECT_NOT_UPLOADED` | S3 업로드가 확인되지 않음 |
| `400` | `TOO_MANY_TAGS` | 임시 기록당 태그 5개 제한 초과 |
| `404` | `TAG_NOT_FOUND` | 태그가 없거나 현재 회원의 태그가 아님 |

### 여행 기록 목록 조회

`GET /api/v1/travel-records`

현재 회원의 기록을 생성일시 내림차순으로 조회한다.

| 파라미터 | 필수 | 설명 |
| --- | --- | --- |
| `countryCode` | 아니요 | 선택 국가 자체와 `root_id`가 해당 국가인 모든 하위 Region 기록 |
| `provinceCode` | 아니요 | 선택 시도 자체와 `parent_id`가 해당 시도인 시·군·구 기록. `countryCode` 필수 |
| `districtCode` | 아니요 | 선택 시·군·구에 직접 저장된 기록. `countryCode`, `provinceCode` 필수 |
| `tagId` | 아니요 | 현재 회원의 해당 태그가 연결된 기록만 조회 |
| `page` | 아니요 | 기본값 `0`, 0 이상 |
| `size` | 아니요 | 기본값 `20`, 1 이상 100 이하 |

지역 조건과 `tagId`를 함께 전달하면 모든 조건을 `AND`로 결합한다. MVP에서는 태그 하나만 필터링하며 여러 태그의 `ANY`/`ALL` 검색은 제공하지 않는다.

#### 조회 예시

```http
# 내 전체 기록
GET /api/v1/travel-records

# 대한민국과 모든 하위 지역 기록
GET /api/v1/travel-records?countryCode=KR

# 제주특별자치도와 하위 시·군·구 기록
GET /api/v1/travel-records?countryCode=KR&provinceCode=49

# 제주시 기록
GET /api/v1/travel-records?countryCode=KR&provinceCode=49&districtCode=50110

# 라멘맛집 태그가 연결된 내 기록
GET /api/v1/travel-records?tagId=12
```

#### Response `200 OK`

```json
{
  "data": {
    "items": [
      {
        "id": 101,
        "title": "비 오는 날의 제주시",
        "regionName": "제주시",
        "startDate": "2026-08-11",
        "endDate": null,
        "thumbnailUrl": null,
        "tags": [
          { "id": 9, "name": "연인" },
          { "id": 12, "name": "라멘맛집" }
        ]
      }
    ],
    "page": 0,
    "size": 20,
    "totalElements": 1,
    "totalPages": 1,
    "hasNext": false
  }
}
```

목록에는 본문과 전체 미디어 목록을 포함하지 않는다. `thumbnailUrl` 필드는 후속 구현을 위해 포함했으며 현재는 `null`을 반환한다.

`tags`는 `tag.created_at ASC, tag.id ASC` 순서로 반환한다.

#### 검증 및 오류 응답

Access Token에서 식별한 현재 회원의 기록만 반환한다.

| 상태 | `code` | 조건 |
| --- | --- | --- |
| `400` | `VALIDATION_ERROR` | 지역 코드 형식이 올바르지 않음, `page < 0`, `size`가 1 미만 또는 100 초과 |
| `400` | `REGION_REQUIRED` | `provinceCode`에 `countryCode`가 없거나, `districtCode`에 `countryCode` 또는 `provinceCode`가 없음 |
| `404` | `MEMBER_NOT_FOUND` | Access Token의 회원이 더 이상 존재하지 않음 |
| `404` | `REGION_NOT_FOUND` | 요청한 국가·시도·시군구 코드가 없음 |
| `400` | `INVALID_REGION_HIERARCHY` | 시도가 선택 국가의 직계 자식이 아니거나, 시군구가 선택 시도의 직계 자식이 아님 |
| `404` | `TAG_NOT_FOUND` | 태그가 없거나 현재 회원의 태그가 아님 |

조건에 맞는 기록이 없는 경우는 오류가 아니며 `200 OK`와 빈 `items`를 반환한다.

### 여행 기록 상세 조회

`GET /api/v1/travel-records/{travelRecordId}`

`travelRecordId`는 양의 정수여야 한다. 현재 회원이 소유한 기록만 조회하며, 기록이 없거나 다른 회원의 기록이면 모두 `404 TRAVEL_RECORD_NOT_FOUND`를 반환한다.

#### Response `200 OK`

```json
{
  "data": {
    "id": 101,
    "title": "비 오는 날의 제주시",
    "content": "골목을 걸으며 오래된 가게들을 기록했다.",
    "region": {
      "country": {
        "code": "KR",
        "name": "대한민국"
      },
      "province": {
        "code": "49",
        "name": "제주특별자치도"
      },
      "district": {
        "code": "50110",
        "name": "제주시"
      }
    },
    "startDate": "2026-08-11",
    "endDate": null,
    "objectKeys": [
      "travel-records/10/550e8400-e29b-41d4-a716-446655440000.jpg"
    ],
    "tags": [
      { "id": 9, "name": "연인" },
      { "id": 12, "name": "라멘맛집" }
    ],
    "createdAt": "2026-08-14T10:30:00",
    "updatedAt": "2026-08-15T09:00:00"
  }
}
```

`objectKeys`는 `record_media.sort_order` 오름차순으로 반환하며 미디어가 없으면 빈 배열이다. 국가 단위 기록은 `province`와 `district`가 `null`이다. Presigned GET URL 변환은 S3 조회 연동 시 추가한다.

### 여행 기록 수정

`PUT /api/v1/travel-records/{travelRecordId}`

현재 회원이 소유한 여행 기록을 생성 요청과 같은 본문으로 전체 수정한다. `objectKeys`는 수정 후 유지할 전체 미디어 목록이며 배열 순서가 노출 순서가 된다. `tagIds`도 수정 후 연결할 전체 태그 목록이며 `null` 또는 빈 배열이면 기존 태그 연결을 모두 제거한다.

- 기존 Object Key가 요청에도 있으면 미디어를 유지하고 순서만 변경한다.
- 요청에서 빠진 기존 Object Key는 `record_media` 연결을 삭제한다.
- 새 Object Key는 미디어로 추가한다.
- `objectKeys`가 `null`이거나 빈 배열이면 모든 미디어 연결을 삭제한다.
- S3 실제 객체 삭제와 Presigned URL 변환은 후속 작업으로 처리한다.

#### Response `200 OK`

수정된 여행 기록을 상세 조회와 같은 응답 구조로 반환한다.

#### 오류 응답

| 상태 | `code` | 조건 |
| --- | --- | --- |
| `400` | `VALIDATION_ERROR` | 회원 ID, 여행 기록 ID 또는 요청 본문이 올바르지 않음 |
| `400` | `INVALID_OBJECT_KEY` | Object Key가 중복되거나 다른 기록에서 사용 중임 |
| `404` | `TRAVEL_RECORD_NOT_FOUND` | 기록이 없거나 현재 회원의 기록이 아님 |

날짜와 Region 관련 오류는 생성 API와 동일하게 처리한다.

### 여행 기록 삭제

`DELETE /api/v1/travel-records/{travelRecordId}`

현재 회원이 소유한 여행 기록을 삭제한다. 기록이 없거나 다른 회원의 기록이면 존재 여부를 숨기기 위해 동일하게 `404 TRAVEL_RECORD_NOT_FOUND`를 반환한다.

연결된 `record_media` 행은 DB의 `ON DELETE CASCADE`로 함께 삭제한다. S3 실제 객체 삭제는 후속 작업으로 처리한다.

#### Response `204 No Content`

응답 본문은 없다.

#### 오류 응답

| 상태 | `code` | 조건 |
| --- | --- | --- |
| `400` | `VALIDATION_ERROR` | 여행 기록 ID가 양의 정수가 아님 |
| `404` | `TRAVEL_RECORD_NOT_FOUND` | 기록이 없거나 현재 회원의 기록이 아님 |

## 6. 지도 요약 API

지도 요약 응답에는 현재 회원의 기록이 있는 Region만 포함한다. 응답에 없는 Region은 앱에서 `count = 0`, `level = NONE`으로 처리한다.

기존 지도 요약 엔드포인트와 `count`, `level` 응답은 구현돼 있다. 이 절의 선택 파라미터 `tagId` 계약은 태그 기능 구현 전 설계다.

두 지도 요약 API는 선택 쿼리 파라미터 `tagId`를 받는다.

| 파라미터 | 필수 | 설명 |
| --- | --- | --- |
| `tagId` | 아니요 | 현재 회원의 해당 태그가 연결된 기록만 집계 |

`tagId`가 없으면 전체 기록을 집계하고, 값이 있으면 태그가 연결된 기록만 집계한다. 지도에 표시할 지역을 태그에 직접 저장하지 않고 `travel_record_tag`와 `travel_record.region_id`를 실시간 집계한다.

### 루트 Region별 지도 색칠 정보 조회

`GET /api/v1/travel-records/map-summary/regions/roots?tagId={tagId}`

#### Response `200 OK`

```json
{
  "data": [
    {
      "regionId": 1,
      "code": "KR",
      "regionType": "COUNTRY",
      "name": "대한민국",
      "count": 12,
      "level": "HIGH"
    }
  ]
}
```

- 기록이 있는 `region_type = COUNTRY` Region을 `code` 오름차순으로 반환한다.
- 현재 회원의 국가 Region 기록과 해당 국가의 모든 하위 Region 기록을 합산한다.
- `regionId`는 후속 하위 Region 지도 요약 요청에 사용한다.
- `code`는 안드로이드 로컬 지도 데이터와 매칭하는 표준 코드다.

| 기록 수 | `level` |
| ---: | --- |
| `0` | `NONE` |
| `1~2` | `LOW` |
| `3~5` | `MEDIUM` |
| `6 이상` | `HIGH` |

### 직속 하위 Region별 지도 색칠 정보 조회

`GET /api/v1/travel-records/map-summary/regions/{regionId}/children?tagId={tagId}`

이전 지도 요약 응답에서 받은 `regionId`를 경로에 전달한다.

#### 요청 예시

```http
GET /api/v1/travel-records/map-summary/regions/1/children?tagId=12
Authorization: Bearer {accessToken}
```

#### Response `200 OK`

```json
{
  "data": [
    {
      "regionId": 15,
      "code": "49",
      "regionType": "PROVINCE",
      "name": "제주특별자치도",
      "count": 5,
      "level": "MEDIUM"
    }
  ]
}
```

- `COUNTRY` ID를 전달하면 기록이 있는 직속 `PROVINCE`를 반환한다.
- `PROVINCE` ID를 전달하면 기록이 있는 직속 `DISTRICT`를 반환한다.
- 각 결과 Region 자신과 모든 하위 Region에 저장된 현재 회원의 기록을 합산한다.
- 정렬 및 색상 단계 규칙은 국가별 조회와 같다.

| 상태 | `code` | 조건 |
| --- | --- | --- |
| `400` | `VALIDATION_ERROR` | `regionId`가 양수가 아님 |
| `404` | `REGION_NOT_FOUND` | 상위 Region이 존재하지 않음 |
| `404` | `TAG_NOT_FOUND` | 태그가 없거나 현재 회원의 태그가 아님 |

### 태그별 집계 규칙

- 한 기록에 같은 태그를 중복 연결할 수 없으므로 한 지역 집계에서 기록을 한 번만 센다.
- 태그는 존재하지만 연결된 기록이 없으면 빈 배열을 반환한다.
- 기존 `count`와 `level` 계약은 유지한다. 태그 필터 적용 후의 `count`를 현재 `LevelPolicy` 기준으로 `NONE`, `LOW`, `MEDIUM`, `HIGH`로 변환한다.
- 데이터 규모와 응답 시간을 측정하기 전에는 별도 태그–지역 집계 테이블을 만들지 않는다. 원본 관계를 실시간 집계해 기록·태그 변경과 지도 결과의 동기화 문제를 피한다.

## 7. 여행 통계 API

여행 통계는 현재 회원이 작성한 전체 기간의 여행 기록을 기준으로 실시간 집계한다. 별도 통계
테이블을 두지 않으므로 여행 기록과 미디어가 변경되면 다음 조회부터 바로 반영된다.

### 내 전체 여행 통계 조회

`GET /api/v1/travel-records/statistics`

#### Response `200 OK`

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
      },
      {
        "regionId": 2,
        "code": "JP",
        "regionType": "COUNTRY",
        "name": "일본",
        "recordCount": 4
      }
    ]
  }
}
```

| 필드 | 설명 |
| --- | --- |
| `recordCount` | 현재 회원의 전체 여행 기록 수 |
| `mediaCount` | 현재 회원의 여행 기록에 연결된 전체 미디어 수 |
| `visitedCountryCount` | 기록이 하나 이상 있는 고유 국가 수 |
| `visitedKoreaDistrictCount` | 기록이 하나 이상 있는 대한민국 고유 시·군·구 수 |
| `visitedCountryCodes` | 방문 국가 ISO 코드의 오름차순 목록. 길이는 `visitedCountryCount`와 같음 |
| `topRegions` | 현재 표시 단계로 합산한 기록 수 상위 3개 |

`topRegions`는 `recordCount DESC, regionId ASC`로 정렬한다. 대한민국 `DISTRICT` 기록은 직속
`PROVINCE`로 올려 합산하고, 해외 기록은 `COUNTRY`로 합산한다. 따라서 현재 응답은 대한민국 시·도와
해외 국가가 함께 포함될 수 있다. 기록이 없으면 모든 숫자는 `0`, 목록은 빈 배열이다.

이 집계 단계는 대한민국은 시·군·구, 해외는 국가 단위로 저장하는 현재 MVP 계약에 맞춘 것이다.
해외 행정구역 저장을 지원할 때 필드와 집계 단계, API 버전을 함께 재검토해야 한다. 자세한 결정과
변경 조건은 [ADR 0016](backend/docs/adr/0016-travel-statistics-read-model.md)을 참고한다.

## 8. 구현 전 확인 사항

- `tag`, `travel_record_tag` Flyway 마이그레이션과 JPA 모델 추가
- 태그 이름 정규화 규칙을 서버와 클라이언트에서 동일하게 적용
- 여행 기록 생성·수정 시 태그 소유권 검증과 연결 변경을 같은 트랜잭션에서 처리
- 지도 요약 Repository에 선택적 `tagId` 조건을 추가하고 MySQL 실행 계획 검증
- 목록·상세 응답에 `tags`를 추가한 뒤 KMP DTO 계약 갱신
- 여행 기록 생성에서도 Object Key 중복·소유권·업로드 완료 검증을 문서 계약과 일치시킴
