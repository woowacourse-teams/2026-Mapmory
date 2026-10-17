# 앱 서비스 의견 제출 API

기준일: 2026-10-14. `backend-release` 응답 형식 기준.

## 요청

```http
POST https://api.map-mory.com/api/v1/service-feedback
Authorization: Bearer {accessToken}
Content-Type: application/json
```

게스트와 소셜 회원 모두 앱 Access Token을 사용한다. 관리자 세션이나 CSRF 토큰은 필요하지 않다.

```json
{
  "category": "BUG_REPORT",
  "content": "사진을 등록할 때 앱이 종료돼요.",
  "platform": "IOS"
}
```

| 필드 | 필수 | 허용 값 및 규칙 |
| --- | --- | --- |
| category | O | `BUG_REPORT`, `FEATURE_REQUEST`, `USABILITY`, `OTHER` |
| content | O | 앞뒤 공백 제거 후 1~2,000자, 공백만 입력 불가 |
| platform | O | `IOS`, `ANDROID` |

유형 표시 이름: `BUG_REPORT` 오류 제보, `FEATURE_REQUEST` 기능 제안, `USABILITY` 사용성 의견, `OTHER` 기타.
JSON enum은 대소문자를 구분하는 문자열이다. 앱 UI에서는 iOS 또는 안드로이드를 선택한다.
회원 ID·접수일·처리 상태는 서버가 지정하므로 요청에 보내지 않는다.

## 성공 응답

`201 Created`, `application/json`:

```json
{
  "data": {
    "feedbackId": 10,
    "createdAt": "2026-10-14T01:00:00"
  }
}
```

| 필드 | 타입 | 의미 |
| --- | --- | --- |
| data.feedbackId | integer (int64) | 접수한 의견 ID |
| data.createdAt | string | 서버 접수 시각 |

현재 `createdAt`에는 `Z`나 시간대 offset이 없고 소수 초가 포함될 수 있다.
UTC 저장 계약에 따라 UTC로 해석한다. 한국 시간 표시가 필요하면 KST로 변환한다.
예시 값은 한국 시간 `2026-10-14 10:00:00`에 해당한다.

앱은 201 응답을 받으면 접수 완료 안내를 표시하고 입력 폼을 초기화한다.
이 서비스는 의견을 받아 개선에 활용하며 사용자 답변·처리 상태 조회 기능은 제공하지 않는다.

## 실패 응답

| 상태 | 상황 | 클라이언트 처리 |
| --- | --- | --- |
| 400 | 필수값 누락, 잘못된 enum·JSON, 빈 내용·길이 초과 | 입력 확인 안내 |
| 401 | 토큰 누락·만료·무효 | 기존 앱 인증 갱신 흐름 적용 |
| 500 | 서버 오류 | 실패 안내, 사용자가 다시 제출할 수 있도록 내용 유지 |

인증 오류 예시 (`401`, `application/problem+json`):

```json
{
  "type": "about:blank",
  "title": "유효하지 않은 인증 토큰입니다.",
  "status": 401,
  "detail": "인증 토큰이 유효하지 않습니다.",
  "instance": "/api/v1/service-feedback",
  "code": "INVALID_ACCESS_TOKEN"
}
```

화면 문구는 상태 코드와 안정적인 오류 코드 기준으로 처리한다.
토큰 만료의 경우 `EXPIRED_ACCESS_TOKEN`이 반환된다.
서버에 중복 제출 방지 키 기능은 없으므로 전송 중 제출 버튼을 비활성화한다.
네트워크 오류 후 재시도하면 중복 접수될 수 있다. 성공 여부가 불명확한 요청을 자동 반복하지 않는다.

관리자 조회·상태 변경 계약은 [백엔드 전체 의견 명세](../../backend/docs/service-feedback-api.md)를 참고한다.
