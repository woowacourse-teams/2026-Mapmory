# 서비스 의견 API 명세

기준일: 2026-10-14. 현재 브랜치에서 구현한 백엔드 계약이다. 배포 여부와 관리자 화면 연결 여부는 별도다.

## API 목록

모든 경로는 `/api/v1` 기준이다.

| Method | 경로 | 기능 | 인증 | 성공 상태 |
| --- | --- | --- | --- | --- |
| POST | `/service-feedback` | 앱 의견 접수 | 앱 Access Token | 201 |
| GET | `/admin/service-feedback` | 관리자 목록 조회 | 관리자 세션 | 200 |
| GET | `/admin/service-feedback/{feedbackId}` | 관리자 상세 조회 | 관리자 세션 | 200 |
| PATCH | `/admin/service-feedback/{feedbackId}/status` | 내부 처리 상태 변경 | 관리자 세션 + CSRF | 200 |

## 인증

앱 제출 요청에는 `Authorization: Bearer {accessToken}`을 보낸다. 게스트 회원도 발급받은 앱 토큰으로 제출할 수 있다.

관리자 요청은 세션 쿠키를 사용한다. 브라우저에서는 `credentials: "include"`를 지정한다.

1. `GET /admin/auth/csrf`로 CSRF 정보를 받는다. 로그인 전에도 호출할 수 있다.
2. 응답의 `headerName`에 `token`을 넣고 `POST /admin/auth/login`으로 로그인한다.
3. 의견 상태 변경에도 같은 방식으로 CSRF 헤더와 세션 쿠키를 보낸다.

CSRF 응답 예시:

```json
{ "data": { "headerName": "X-CSRF-TOKEN", "token": "발급된 토큰" } }
```

로그인 요청:

```json
{ "username": "관리자 아이디", "password": "관리자 비밀번호" }
```

로그인 성공 시 `200`과 `{ "data": { "username": "관리자 아이디" } }`를 반환한다.
현재 관리자 확인은 `GET /admin/auth/me`, 로그아웃은 CSRF 헤더를 포함한 `POST /admin/auth/logout`이다.
로그아웃 성공 응답은 `204`이며 본문이 없다.

## 공통 값

enum은 JSON 문자열로 주고받으며 대소문자를 구분한다.

| 필드 | 허용 값 | 화면 표시 |
| --- | --- | --- |
| category | `BUG_REPORT` | 오류 제보 |
| category | `FEATURE_REQUEST` | 기능 제안 |
| category | `USABILITY` | 사용성 의견 |
| category | `OTHER` | 기타 |
| platform | `IOS`, `ANDROID` | iOS, 안드로이드 |
| status | `UNCONFIRMED` | 미확인 |
| status | `IN_PROGRESS` | 반영 중 |
| status | `COMPLETED` | 반영 완료 |
| status | `ON_HOLD` | 보류 |
| status | `NOT_IMPLEMENTED` | 미반영 |

응답 시각은 `LocalDateTime`을 직렬화한 문자열이다. 현재 응답에는 `Z`나 UTC offset이 없다. 예: `2026-10-14T01:00:00`. 소수 초가 포함될 수 있다. UTC 저장 계약에 따라 클라이언트에서 UTC로 해석한다.
화면에서는 한국 시간으로 변환한다. UTC 저장을 유지하려면 생성 시각에 영향을 주는 JVM 시간대도 UTC여야 한다.
현재 `BaseEntity`는 `@CreationTimestamp`와 `LocalDateTime`을 사용하며, `hibernate.jdbc.time_zone`만으로 생성 시각의 시간대까지 보장하지는 않는다.

## 앱 의견 제출

`POST /service-feedback`

```json
{
  "category": "BUG_REPORT",
  "content": "사진을 등록할 때 앱이 종료돼요.",
  "platform": "IOS"
}
```

| 필드 | 규칙 |
| --- | --- |
| category | 필수, 허용된 유형 |
| content | 필수, 공백만 있는 내용 불가, 앞뒤 공백 제거 후 최대 2,000자 |
| platform | 필수, `IOS` 또는 `ANDROID` |

내용 앞뒤 공백은 저장 전에 제거한다. 작성자는 토큰에서 가져오고 접수 시각은 서버가 생성한다.
초기 상태는 `UNCONFIRMED`다. 상태를 지정하는 입력 필드는 없다.

성공 `201`:

```json
{ "data": { "feedbackId": 10, "createdAt": "2026-10-14T01:00:00" } }
```

사용자는 접수 결과만 받는다. 답변, 사용자 의견 수정·삭제, 사용자 처리 상태 조회 API는 제공하지 않는다.

## 관리자 목록 조회

`GET /admin/service-feedback?page=0&size=20&status=UNCONFIRMED&category=BUG_REPORT&platform=IOS`

| 파라미터 | 기본값 | 규칙 |
| --- | --- | --- |
| page | 0 | 0 이상의 정수 |
| size | 20 | 1~100 |
| status | 없음 | 생략하면 전체 상태 |
| category | 없음 | 생략하면 전체 유형 |
| platform | 없음 | 생략하면 전체 OS |
| from | 없음 | 시작 날짜 `YYYY-MM-DD`, 한국 시간 기준 포함 |
| to | 없음 | 종료 날짜 `YYYY-MM-DD`, 한국 시간 기준 포함 |

모든 필터는 AND로 적용하며 접수 시각 내림차순, ID 내림차순으로 정렬한다.
전체 건수는 적용된 필터 기준이다. 날짜는 KST 시작부터 종료일 다음 날 시작 전까지 UTC 구간으로 바꿔 조회한다. `from > to`이면 400이다. 내용 검색·상태별 요약 건수 API는 구현하지 않았다.

성공 `200`:

```json
{
  "data": {
    "feedback": [
      {
        "feedbackId": 10,
        "category": "BUG_REPORT",
        "content": "사진을 등록할 때 앱이 종료돼요.",
        "platform": "IOS",
        "status": "UNCONFIRMED",
        "createdAt": "2026-10-14T01:00:00"
      }
    ],
    "page": 0,
    "size": 20,
    "totalElements": 1,
    "totalPages": 1
  }
}
```

결과가 없으면 `feedback: []`, `totalElements: 0`, `totalPages: 0`이다. 다음 페이지 여부는 `page + 1 < totalPages`로 판단한다.
작성자 ID·닉네임·회원 객체는 관리자 의견 응답에 포함하지 않는다.

## 관리자 상세 조회

`GET /admin/service-feedback/{feedbackId}`

`feedbackId`는 양수다. 성공 `200`은 목록의 항목 하나를 `data`에 담는다.
상세를 열어도 처리 상태는 자동으로 변경하지 않는다.

## 처리 상태 변경

`PATCH /admin/service-feedback/{feedbackId}/status`

```json
{ "status": "COMPLETED" }
```

`status`는 필수다. 허용된 다섯 상태 사이에서 자유롭게 변경할 수 있다.
성공 `200`은 변경된 의견을 상세 조회와 같은 형태로 반환한다.

```json
{
  "data": {
    "feedbackId": 10,
    "category": "BUG_REPORT",
    "content": "사진을 등록할 때 앱이 종료돼요.",
    "platform": "IOS",
    "status": "COMPLETED",
    "createdAt": "2026-10-14T01:00:00"
  }
}
```

접수 시각·내용은 유지된다. DB에는 수정 시각이 갱신된다. 상태 변경 전·후 값을 `admin_audit_log`에 저장한다.

## 오류

| HTTP 상태 | 조건 |
| --- | --- |
| 400 | 잘못된 enum·JSON, 필수값 누락, 길이 제한, 잘못된 페이지·ID |
| 401 | 앱 토큰이 없거나 유효하지 않음, 관리자 세션 없음, 로그인 실패 |
| 403 | 관리자 권한 없음 또는 CSRF 토큰이 없거나 유효하지 않음 |
| 404 | 의견 ID가 존재하지 않음: `SERVICE_FEEDBACK_NOT_FOUND` |

인증·CSRF 검사에 실패하면 본문 검증보다 해당 오류가 먼저 반환될 수 있다.
오류 응답은 `application/problem+json`이다. 의견이 없는 경우 예시:

```json
{
  "type": "about:blank",
  "title": "서비스 의견을 찾을 수 없습니다.",
  "status": 404,
  "detail": "요청한 서비스 의견이 존재하지 않습니다.",
  "instance": "/api/v1/admin/service-feedback/999",
  "code": "SERVICE_FEEDBACK_NOT_FOUND"
}
```
