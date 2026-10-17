# ADR 0009. 인증·인가에 Spring Security와 자체 JWT를 사용한다

- 상태: 채택
- 날짜: 2026-08-14
- 관련: #45, ADR 0006

---

## 문제

기존 인증은 `@RequestHeader("X-Member-Id")` 헤더로 회원을 식별하는 임시 방식이었다.
누구나 헤더 값을 바꾸면 다른 회원을 사칭할 수 있어, 소셜 로그인을 붙이기 전에
토큰 검증 기반의 인증 계층이 먼저 필요하다.

클라이언트는 안드로이드/iOS 네이티브 앱이며, 로그인 후에는 발급받은 토큰으로만
계속 통신한다.

## 결정

### Spring Security를 도입하고 우리 서비스의 JWT(access token)를 직접 발급·검증한다

- access token은 HS256으로 서명한 JWT다. 페이로드의 `sub`에 memberId를 담는다.
- 토큰 자체의 서명·만료 검증에는 DB 조회가 필요 없다.
- 발급/검증 로직은 `JwtProvider`에 모으고, 키·만료는 `jwt.*` 설정으로 주입한다.
  (`jwt.secret`은 프로파일별: local은 개발용, prod는 환경변수)

### 세션은 STATELESS로 둔다

서버는 `HttpSession`을 만들지 않는다. 매 요청마다 필터가 토큰을 검증해
`SecurityContext`를 새로 구성한다. 다중 기기·수평 확장에 유리하다.

### 인증은 필터에서, Member 주입은 ArgumentResolver로 한다

- `JwtAuthenticationFilter`가 `Authorization: Bearer` 토큰을 검증해
  `SecurityContext`에 memberId를 등록한다.
- `LoginMemberArgumentResolver`는 `SecurityContext`의 memberId로 회원을 조회하고,
  컨트롤러는 `@LoginMember`로 인증된 `Member`를 주입받는다. (`X-Member-Id` 헤더 제거)
- `@LoginMember`가 선언된 요청은 현재 회원의 존재를 확인하기 위해 DB를 한 번 조회한다.

> 2026-10-14 구현 업데이트(#342): `LoginMemberArgumentResolver`는 회원을 조회하지 않고
> `EntityManager.getReference`로 id만 가진 프록시를 주입한다. open-in-view가 꺼져 있어 여기서 조회하면
> 서비스 트랜잭션과 별개로 커넥션을 한 번 더 빌렸고, 부하 테스트에서 커넥션 풀이 먼저 고갈됐다.
> 그래서 회원의 존재를 더는 확인하지 않는다.
>
> - 토큰은 유효한데 회원 행이 없으면 `404 MEMBER_NOT_FOUND`를 내지 않는다. 조회 요청은 `200`과 빈 결과를,
>   회원을 참조해 저장하는 요청(태그·여행 기록 생성 등)은 외래 키 위반으로 `500`을 낸다.
>   500이라 ERROR 로그와 운영 알람이 함께 울린다.
> - 지금은 회원 삭제 기능이 없어 이 상황이 생기지 않는다. 회원 행을 지우려면 `refresh_token`부터 지워야 해서
>   (외래 키) 회원이 사라진 뒤에는 새 access token을 받을 수 없다. 남은 access token 유효 시간(30분) 동안만 생긴다.
> - 회원 삭제(탈퇴)를 만들 때 이 경로를 함께 다룬다. 예를 들어 탈퇴한 회원의 토큰을 거절하거나,
>   존재 확인을 서비스 트랜잭션 안으로 옮긴다.
> - 프록시에서 id 외의 필드를 읽으면 `LazyInitializationException`이 난다. 회원 정보가 필요하면
>   서비스 트랜잭션 안에서 조회한다.

### 필터 단계의 인증·인가 실패도 Problem Details로 응답한다

필터에서 발생한 실패는 `@RestControllerAdvice`가 잡지 못한다. 따라서
`AuthenticationEntryPoint`(401)와 `AccessDeniedHandler`(403)를 두어,
ADR 0006과 동일한 `application/problem+json` 형식으로 응답한다.
오류의 의미는 `AuthErrorCode`(`ErrorKind` 기반)로 표현하고, HTTP 상태 매핑은 웹 계층에 남긴다.

## 검토한 대안

### 인터셉터 + ArgumentResolver로 직접 구현

채택하지 않았다. 가볍고 투명하지만, URL·메서드 단위 인가 선언, 보안 헤더,
표준 예외 처리 등을 직접 재구현해야 한다. 특히 향후 역할/권한이 생기면
검증된 표준 스택 없이 확장 비용이 커진다.

우리 카카오 플로우는 앱이 토큰을 전달하는 방식(ADR 0010)이라, Spring Security의
redirect 기반 `oauth2-client` 자동화 이득은 받지 않는다. 그러나 요청마다 토큰을
검증하는 필터·인가·SecurityContext 이점은 그대로 얻는다.

### 세션 기반 인증

채택하지 않았다. 서버가 로그인 상태를 보관해야 하며, 모바일 다중 기기와
수평 확장에 부적합하다.

## 결과

### 장점

- 로그인 후에는 토큰만으로 통신하며, 서버는 무상태로 검증한다.
- 비즈니스 로직이 HTTP 상태 코드에 결합되지 않는다(`ErrorKind` → 웹 계층 매핑).
- 인증·인가 실패까지 오류 응답 형식이 통일된다.

### 비용과 주의점

- Spring Security의 러닝 커브와, 필터 계층 예외를 별도 처리기로 다뤄야 하는 점.
- `@LoginMember`가 선언된 요청마다 회원 조회가 한 번 발생한다.
  (2026-10-14 #342부터는 조회하지 않는다. 대신 없는 회원의 토큰을 걸러 내지 못한다. 위 구현 업데이트 참고)
- `ErrorKind`가 추가되면 웹 계층의 HTTP 상태 매핑도 함께 추가해야 한다.
- refresh token 전략은 별도 결정으로 다룬다(후속).
