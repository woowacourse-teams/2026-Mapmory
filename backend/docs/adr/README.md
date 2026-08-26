# Architecture Decision Records

Mapmory 백엔드의 주요 설계 결정을 기록한다.

| 번호 | 결정 | 상태 |
| --- | --- | --- |
| [0001](0001-infrastructure-design.md) | 인프라 설계 | 승인 |
| [0002](0002-technology-stack-design.md) | 기술 스택 설계 | 승인 |
| [0003](0003-erd-design.md) | ERD 설계 | 대체됨 (ADR 0008) |
| [0004](0004-region-code-system.md) | 지역 코드 체계와 계층 판단 방식 | 채택 |
| [0005](0005-flyway-schema-management.md) | Flyway 스키마 관리 | 채택 |
| [0006](0006-use-problem-details-for-api-errors.md) | API 오류 응답에 Problem Details 사용 | 채택 |
| [0007](0007-travel-record-country-and-location.md) | 여행 기록의 국가와 세부 지역 저장 방식 | 대체됨 (ADR 0008) |
| [0008](0008-unify-country-and-location-as-region.md) | 국가와 행정구역을 Region 계층으로 통합 | 채택 |
| [0009](0009-region-map-summary-api.md) | Region 지도 요약 API와 테스트 범위 | 채택 | 
| [0010](0009-authentication-with-spring-security-and-jwt.md) | 인증·인가에 Spring Security와 자체 JWT 사용 | 채택 |
| [0011](0010-social-login-and-member-identifier.md) | 소셜 로그인(카카오 토큰 검증)과 회원 식별자 분리 | 채택 |
| [0012](0011-refresh-token-rotation.md) | refresh 토큰 해시 저장·회전·폐기와 재사용 감지 | 채택 |
| [0013](0013-user-created-tags-and-travel-record-association.md) | 사용자 생성 태그와 여행 기록 연결 설계 | 채택 |
| [0014](0014-application-logging-and-metrics.md) | 애플리케이션 로그와 메트릭 공통 관측 규칙 | 채택 |
| [0015](0015-guest-login.md) | 게스트 로그인의 인증 파이프라인 재사용과 회원 승격 | 채택 |
| [0016](0016-verify-uploaded-object-before-linking.md) | 기록에 붙는 S3 객체의 실존을 저장 시점에 확인 | 채택 |
