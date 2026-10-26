# 그룹 탭 소셜 로그인

## 사용자 흐름

앱 시작에서는 지도와 개인 기록을 계속 사용한다. 그룹 탭은 `/members/me`의 provider를 확인하고 게스트에게만 로그인 안내를 보여 준다. SDK는 제공자 버튼을 누를 때 실행한다. 제공자 인증, 서버 토큰 교환, 기기 저장, 회원 확인을 분리한다. 완료 후 그룹 탭으로 돌아와 `/groups` 목록을 조회한다. 그룹 조회 실패는 로그인 성공을 취소하지 않으며 목록만 다시 조회한다.

그룹 앨범의 샘플 화면·도메인·미리보기 GA는 #485와 #486에서 별도로 관리하며 이 로그인 PR에는 포함하지 않는다. 이번 연결 범위는 로그인과 실제 그룹 목록이다. 그룹 생성·초대·앨범·사진 API와 상세 UI의 운영 연결은 후속 범위다. `/groups`가 배포되지 않아 404이면 준비 안내와 재시도를 제공한다.

## API 기준

`backend-develop`의 OIDC 계약을 사용한다. `backend-release`의 이전 카카오 access-token 계약과 호환되지 않는다.

| 요청 | body |
| --- | --- |
| POST /auth/login/google | idToken |
| POST /auth/login/kakao | idToken |
| POST /auth/login/apple | identityToken |

게스트 accessToken을 Bearer로 전달하고 응답의 accessToken/refreshToken을 함께 교체한다. 카카오 SDK는 Android/iOS 모두 idToken을 사용하므로 제공자 콘솔의 OpenID Connect 활성화가 필요하다. 설정 키가 없거나 ID token이 없으면 성공으로 처리하지 않는다.

## 계정과 저장 일관성

- #438이 머지된 develop을 기반으로 제공자 인증·서버 연결·인증 GA를 분리했다. 그룹 미리보기·도메인·그룹 상세 GA는 #485/#486에서 관리한다. 백엔드 소스는 변경하지 않는다.
- 저장소 읽기 실패와 토큰 없음은 구분한다. 실패 시 토큰 삭제나 신규 게스트 생성으로 복구하지 않는다.
- 공통 HTTP 경계는 EXPIRED_ACCESS_TOKEN일 때만 refresh 후 그 HTTP 요청 하나를 최대 한 번 재전송한다. 소셜 세션 계층은 추가 재전송하지 않는다.
- 세션 전환 중에는 새 계정 요청·사진 저장을 막는다. 진행 중인 repository 호출, 대기·실패한 백그라운드 저장, 영상 생성 요청이 있으면 계정 전환을 시작하지 않는다. 개인 기록 호출은 업로드, 캐시 쓰기, 기기 사진 인덱스 갱신이 끝날 때까지 세션 사용을 유지한다.
- 서버 교환 전에 pending provider를 저장한다. 서버가 응답하기 전에 프로세스가 종료되어도 신규 게스트를 자동 생성하지 않는다. 제공자 증명 자체는 메모리에만 둔다.
- 새 토큰을 받은 뒤 저장 실패 시 새 토큰을 메모리에 유지한다. 결과 확인은 토큰 저장부터 재시도하며 소비된 옛 refresh를 다시 쓰지 않는다.
- 토큰 저장 성공 이후에만 pending 상태를 `provider:saved`로 기록한다. 재시작 시 `/members/me`가 소셜 회원을 반환한다는 사실만으로는 성공을 확정하지 않는다. 서버 승격 이후 옛 게스트 accessToken이 소셜 회원을 가리킬 수 있지만 그 refresh는 이미 폐기되었을 수 있기 때문이다.
- `:saved`가 없는 재시작 복구는 같은 제공자와 같은 계정의 재인증이 필요하다. 보존된 증명이 있는 응답 유실은 사용자가 결과 확인을 눌러 같은 증명으로 교환을 다시 시도한다. 서버의 최종 결과를 확인하기 전에는 다른 계정 요청을 막는다.
- 회원 확인과 캐시 무효화가 끝나야 완료 상태와 새 navigation generation을 발행한다. 목록·지도·통계·영상 작업·기기 사진 사용 인덱스를 무효화하고 이전 navigation/ViewModel 상태를 교체한다. 사진 파일 캐시 자체는 원본 Object Key별 저장을 유지하며 이전 계정의 목록은 재사용하지 않는다.
- 유효한 게스트가 처음 소셜 계정을 연결하면 기록이 이어진다. 기존 소셜 계정으로 전환되면 자동 병합하지 않으며 guestAbandoned 안내를 표시한다. 이 안내 상태도 pending 저장 확인 표식에 포함한다.
- 무효·만료된 refresh 또는 INACTIVE_MEMBER는 일반 API 재시도를 중단한다. 사용자가 게스트 기록 연결을 보장할 수 없다는 안내에 동의한 경우에만 기존 세션 Bearer 없이 명시적으로 소셜 계정 로그인을 시도한다.

## 예외와 관측

SDK 취소는 정상 결과다. 제공자 토큰 거절은 다시 인증할 수 있게 한다. 네트워크/5xx/응답 해석 실패는 서버 반영 여부가 불확실하므로 pending 상태로 유지한다. 저장 실패와 회원 확인 실패는 해당 단계부터 이어서 진행한다. 인증 요청에는 20초 제한을 적용하고 대용량 사진 업로드에는 이 제한을 적용하지 않는다.

| GA | 의미 |
| --- | --- |
| social_login_viewed/started/completed | 기존 제공자 인증 의미 유지. source=group_tab, stage=provider_auth, account_linked=false |
| auth_login_started/completed | 앱 로그인 시도와 결과. success/failure/cancelled/blocked/pending, stage, duration_ms |
| login | 새 토큰 저장·회원 확인·캐시 전환 완료. method=google/kakao/apple. 자동 세션 복원은 제외 |
| group_list_loaded | 실제 목록 조회 결과. execution_mode=live; 로그인 필요는 별도 결과 |
| app_error | 실제 SDK/서버/저장 오류. 취소·정책 차단은 제외 |

GA는 SDK 인증 성공과 계정 로그인 성공을 합산하지 않는다. pending은 확정 실패도 성공도 아니다. 결과 확인은 별도 앱 로그인 시도이며 내부 HTTP 재전송은 별도 완료 이벤트를 만들지 않는다. `isNewMember=false`가 게스트 승격인지 기존 회원인지 구분하지 않으므로 sign_up은 발행하지 않는다. 토큰·nonce·코드·이름·이메일·그룹 ID·원본 오류 메시지를 전송하지 않는다. Analytics 실패가 인증 결과를 바꾸지 않도록 기존 안전한 emit/reportFailure 경계를 유지한다.

## 서버/제공자 설정과 검증 한계

서버 환경변수의 audience 허용값과 앱 클라이언트 ID, 카카오 OIDC, Google SHA/패키지·OAuth 클라이언트, iOS URL scheme·Apple entitlement가 일치해야 한다. Apple nonce의 서버 검증은 현재 API 계약에 포함되어 있지 않아 별도 서버 보강이 필요하다.

서버 성공 직후 응답 유실과 토큰 저장 전 프로세스 종료를 포함한 정확한 1회 처리, guestAbandoned 원래 결과의 재현은 클라이언트만으로 보장하지 않는다. 동일 로그인 요청의 서버 멱등 키/결과 재조회 계약이 있으면 보완할 수 있다. 복구 중 SDK에서 다른 소셜 계정을 선택하면 원래 로그인 시도를 복원한다고 보장할 수 없다.

실제 제공자 계정 성공·운영 서버 배포·Firebase 수신은 단위 테스트나 에뮬레이터 fixture 테스트와 구분한다. 토큰 저장/응답 유실/동시 호출은 공통 회귀 테스트에서, 화면 성공·취소·저장 재시도·목록 재시도는 분리된 테스트 계정 fixture로 에뮬레이터에서 검증한다.

## PR 분리 검증 (2026-10-27)

#438이 머지된 develop 0f626089에서 인증 변경만 분리했다. 통합본은 로컬 `and/backup/social-login-integration-20261027`에 보관한다. 그룹 미리보기 코드는 #485, 그룹 미리보기 GA는 #486에 남긴다. 기존 develop의 영상 GA를 보존했다.

- JVM 전체 테스트 401개, 실패·오류 0.
- Android debug/app-test 빌드 성공, 에뮬레이터 로그인·로그아웃 UI 테스트 6개 성공.
- Xcode Debug iOS Simulator 전체 빌드 성공.
- 실제 제공자/서버의 최종 로그인·로그아웃, iOS Google Client ID 설정, 운영 카카오 API 규격 및 Firebase 수신은 기존 미검증 항목으로 유지한다.
