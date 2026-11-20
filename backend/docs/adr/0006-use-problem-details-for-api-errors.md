# ADR 0006: API 오류 응답에 Problem Details를 사용한다

- 상태: 채택
- 결정일: 2026-08-10

## 배경

API마다 오류 응답 구조가 달라지면 클라이언트는 HTTP 상태, 메시지, 필드 오류를 서로 다른 방식으로 해석해야 한다. 서버에서도 도메인 오류와 HTTP 표현이 섞이기 쉬우며, 처리하지 못한 예외의 내부 메시지가 응답에 노출될 가능성이 있다.

Mapmory는 다음 요구사항을 만족하는 공통 오류 처리 방식이 필요하다.

- 모든 HTTP 오류가 예측 가능한 형태로 응답되어야 한다.
- 클라이언트는 사용자 메시지가 아니라 안정적인 오류 코드로 분기할 수 있어야 한다.
- 요청값 검증 오류는 문제가 발생한 필드와 사유를 함께 제공해야 한다.
- 비즈니스 계층은 Spring MVC의 `HttpStatus`에 의존하지 않아야 한다.
- Spring MVC가 이미 처리하는 오류까지 애플리케이션이 중복 구현하지 않아야 한다.
- 예상하지 못한 예외는 내부 정보를 노출하지 않으면서 서버 로그에는 원인을 남겨야 한다.

## 결정

### RFC 9457 Problem Details를 오류 응답의 기본 형식으로 사용한다

Spring의 `ProblemDetail`을 사용하고 오류 응답의 미디어 타입을 `application/problem+json`으로 통일한다.

기본 필드는 다음 의미로 사용한다.

- `title`: 오류 유형을 설명하는 짧고 사람이 읽을 수 있는 문구
- `status`: HTTP 상태 코드
- `detail`: 현재 오류에 대한 사용자 공개 설명
- `instance`: 오류가 발생한 요청의 경로 패턴(2026-11-20 보완 참고)
- `type`: 오류 유형을 식별할 URI가 정해지기 전까지 기본값을 사용하며, 관련 문서 체계가 마련되면 확장한다.

클라이언트 처리를 위해 다음 확장 필드를 사용한다.

- `code`: 비즈니스 오류를 구분하는 안정적인 식별자
- `errors`: 요청값 검증에 실패한 필드와 사유의 목록

응답 생성 규칙은 `ProblemDetailFactory`에 모으되, 예외의 종류를 판별하거나 HTTP 상태를 결정하는 책임은 두지 않는다.

### 예외의 종류별로 처리 책임을 분리한다

하나의 거대한 전역 예외 처리기 대신 다음과 같이 역할을 나눈다.

- `BusinessExceptionHandler`: 예상 가능한 비즈니스 오류
- `ValidationExceptionHandler`: 요청값 검증 오류
- Spring MVC 기본 처리: 지원하지 않는 HTTP 메서드 등 프레임워크가 알고 있는 오류
- `UnexpectedExceptionHandler`: 위 범위에서 처리하지 못한 예외의 최종 안전망

Spring MVC 기본 오류는 `spring.mvc.problemdetails.enabled=true`로 활성화한 Spring Boot의 기본 `ResponseEntityExceptionHandler` 계열 처리에 맡긴다. 현재 기본 응답을 별도로 변경할 요구가 없으므로 애플리케이션에서 `ResponseEntityExceptionHandler`를 상속하지 않는다. 기본 MVC 오류의 응답 계약을 변경해야 할 때만 상속 또는 별도 재정의를 검토한다.

### 비즈니스 계층은 HTTP 상태 대신 오류의 의미를 표현한다

`BusinessException`은 호출자가 복구를 강제할 수 없는 비즈니스 실패를 나타내므로 `RuntimeException`을 상속한다.

도메인별 오류 enum은 `ErrorCode`를 구현하고 다음 정보를 제공한다.

- `ErrorKind`: 입력 오류, 인증 필요, 접근 거부, 리소스 없음, 충돌과 같은 전송 계층 독립적인 의미
- `code`: 클라이언트 분기용 식별자
- `title`, `detail`: 공개 가능한 기본 설명

`BusinessExceptionHandler`가 `ErrorKind`를 `HttpStatus`로 변환한다. 따라서 도메인과 비즈니스 로직은 Spring MVC에 의존하지 않으며, HTTP 표현에 관한 결정은 웹 계층에 남는다.

오류 코드는 하나의 거대한 enum으로 모으지 않는다. 도메인별 enum으로 나누고 공통 `ErrorCode` 인터페이스 뒤에서 동일하게 처리한다. 새로운 오류를 찾고 변경할 때 해당 도메인의 문맥을 함께 볼 수 있고, enum 간 이름 충돌이나 불필요한 결합도 줄일 수 있기 때문이다.

### 검증 실패와 서버 내부 실패를 구분한다

요청 본문, 메서드 파라미터, 교차 파라미터 검증 실패는 400과 `VALIDATION_ERROR`로 응답한다. 각 오류는 `FieldErrorDetail`의 `field`와 `detail`로 표현한다.

컨트롤러 반환값 검증 실패는 클라이언트가 수정할 수 있는 요청 오류가 아니므로 안전한 500 응답으로 처리하고 전체 원인은 서버 로그에 기록한다.

예상하지 못한 예외도 500으로 처리한다. 예외 메시지나 스택 트레이스는 응답에 포함하지 않고, 요청 메서드와 경로를 포함한 전체 예외를 `ERROR` 로그로 남긴다.

## 검토한 대안

### 자체 오류 DTO 또는 `ApiProblemDetail`을 정의한다

채택하지 않았다. 표준 필드를 다시 정의하면 Spring의 기본 Problem Details 처리와 직렬화 기능을 중복 구현하게 된다. 별도 타입의 역할도 응답 모양을 감싸는 것 이상으로 명확하지 않았다. 필요한 애플리케이션 필드는 Spring `ProblemDetail`의 확장 속성으로 추가한다.

### 모든 응답을 공통 `ApiResponse`로 감싼다

채택하지 않았다. 성공 응답과 오류 응답은 목적과 표준이 다르다. 오류까지 자체 래퍼로 감싸면 `application/problem+json`과 Problem Details의 표준 구조를 그대로 사용할 수 없다.

### `ErrorCode`가 `HttpStatus`를 직접 가진다

채택하지 않았다. 구현은 단순해지지만 도메인 오류 정의가 Spring MVC와 HTTP 전송 규칙에 결합된다. 같은 비즈니스 오류를 다른 인터페이스에서 표현하기도 어려워진다.

### 하나의 `GlobalExceptionHandler`에서 모든 예외를 처리한다

채택하지 않았다. 비즈니스 오류 매핑, 검증 결과 변환, 로깅, 응답 생성, 최종 안전망이 한 클래스에 모여 변경 이유가 많아진다. 예외 범주별 처리기와 응답 생성 팩토리로 책임을 분리한다.

### `ResponseEntityExceptionHandler`를 직접 상속한다

현재는 채택하지 않았다. Spring Boot가 기본 MVC 예외를 이미 Problem Details로 처리하므로 단순 위임 코드를 만들 실익이 없다. 기본 오류에 공통 확장 필드를 넣거나 메시지를 변경해야 하는 요구가 생기면 다시 검토한다.

## 결과

### 장점

- 클라이언트가 표준 필드와 안정적인 `code`를 기준으로 오류를 처리할 수 있다.
- 비즈니스 로직과 HTTP 상태 코드의 결합을 피할 수 있다.
- 프레임워크 기본 처리와 애플리케이션 예외 처리가 중복되지 않는다.
- 예상하지 못한 오류의 내부 정보 노출을 막으면서 디버깅에 필요한 로그를 남긴다.
- 처리기별 책임과 테스트 범위가 명확해진다.

### 비용과 주의점

- `ErrorKind`가 추가될 때 웹 계층의 HTTP 상태 매핑도 함께 추가해야 한다.
- 도메인별 오류 enum이 늘어나므로 패키지와 이름 규칙을 일관되게 유지해야 한다.
- 클래스 단위 검증 오류와 `ConstraintViolationException`의 발생 출처를 구분하는 정책을 보완해야 한다.
- `type` URI를 도입하려면 오류 문서의 주소와 버전 관리 정책이 먼저 필요하다.
- 공개용 `detail`에는 내부 구현 정보나 민감정보를 포함하지 않아야 한다.

## 검증 원칙

다음 동작을 자동화된 테스트로 유지한다.

- 비즈니스 오류의 상태, `code`, 공개 메시지
- 요청값 검증 오류의 `errors`
- 반환값 검증 실패의 안전한 500 응답
- Spring MVC 기본 오류의 `application/problem+json` 응답
- 예상하지 못한 예외의 내부 메시지 비노출

## 참고

- [RFC 9457: Problem Details for HTTP APIs](https://www.rfc-editor.org/rfc/rfc9457)
- [Spring Framework: Error Responses](https://docs.spring.io/spring-framework/reference/web/webmvc/mvc-ann-rest-exceptions.html)

## 2026-11-20 보완: `instance`는 원본 경로가 아니라 경로 패턴이다 (#744)

`instance`에 원본 요청 경로를 넣으면 경로 변수 값이 응답에 실린다. 초대 미리보기·수락처럼 경로로 초대 토큰을 받는 API에서는, 그 토큰을 아는 사람이 그룹에 들어갈 수 있다.

- `instance`는 매칭된 경로 패턴이다. 예: `/api/v1/invitations/%7BtokenOrCode%7D/accept`. `{}`는 URI에 그대로 쓸 수 없어 퍼센트 인코딩된다.
- 패턴이 없으면 `UNKNOWN`이다. 시큐리티 필터에서 끝난 401·403, 핸들러 매칭이 실패한 405·415가 여기에 해당한다. 컨트롤러에 없는 경로는 정적 리소스 핸들러의 `/**`가 된다.
- Spring이 직접 만드는 ProblemDetail도 `ProblemDetailInstanceAdvice`가 응답 직전에 같은 값으로 바꾼다.
- 매핑되지 않은 경로의 404는 `detail`에 경로를 넣지 않는 고정 문구로 응답한다.
- `/error` 응답의 `path`는 `spring.web.error.include-path: never`로 끈다.
- 오류 로그의 `uri` 필드도 같은 패턴이다(`RequestPaths.patternOf`).

`instance`로 리소스 id를 찾을 수는 없다. 특정 요청을 추적할 때는 응답 헤더의 `X-Request-Id`로 로그를 찾는다.
