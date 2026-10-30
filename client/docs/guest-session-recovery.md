# 게스트 세션 보존과 재설치 복구 결정

- 상태: 채택, Android 복구 저장소 구현은 후속 작업
- 결정일: 2026-10-01
- 적용 범위: iOS·Android 게스트 인증과 로컬 캐시

## 배경

Mapmory의 기록은 서버에서 게스트 회원에 귀속된다. 앱이 인증 정보를 잃은 상태에서 새
게스트 로그인을 자동 수행하면 서버에는 다른 회원이 생기고, 기존 기록이 사라진 것처럼
보인다. 따라서 Access Token의 만료와 기존 게스트의 신원 상실을 같은 상황으로 처리하면
안 된다.

앱 삭제 후 재설치가 대표적인 신원 상실 경로다. 로컬에 기록 사본을 두더라도 새 게스트에게
기록을 자동 재전송하면 중복 기록, 미디어 중복 업로드, 서버에서 수정·삭제된 상태와의 충돌이
생긴다. 로컬 기록 사본은 화면 표시와 장애 복구 보조 수단으로만 사용하고, 회원 신원을
대체하는 근거로 사용하지 않는다.

## 결정

1. 저장된 Refresh Token이 있으면 새 게스트를 만들지 않고 기존 `/auth/token/refresh`로
   세션을 복구한다. 발급된 Access Token과 회전된 Refresh Token은 즉시 다시 저장한다.
2. 저장된 세션의 갱신이 실패해도 새 게스트 로그인을 자동 실행하지 않는다. 기존 회원의
   소유권을 잃을 수 있으므로 로그인 만료 오류를 보여준다.
3. 앱을 삭제한 뒤에도 플랫폼이 허용하는 범위에서 Refresh Token을 복원할 수 있는 저장소를
   사용한다.
   - iOS: Keychain을 사용한다. 기존 `UserDefaults` 값은 최초 조회 시 Keychain으로
     마이그레이션한다.
   - Android: 일반 `SharedPreferences`는 실행 중의 기본 저장소로 유지하고, Google Play
     services의 Block Store에 복구용 Refresh Token을 함께 저장한다. 새 설치에서
     `SharedPreferences`가 비어 있으면 Block Store를 조회한 뒤 기존 refresh API로 토큰
     쌍을 다시 발급받는다.
4. 토큰이 회전될 때 로컬 저장소와 복구 저장소를 함께 갱신한다. 로그아웃이나 사용자가
   명시적으로 새 게스트를 시작할 때 두 저장소를 함께 비운다.
5. `auth_session_refresh_failed` 이벤트는 프로덕션 환경에서만, 앱 실행당 최대 한 번
   기록한다. 토큰, 회원 식별자, 기록 내용은 Analytics 파라미터에 포함하지 않는다.

## 현재 구현 상태

| 플랫폼 | 현재 저장소 | 앱 삭제 후 복구 | 판단 |
| --- | --- | --- | --- |
| iOS | Keychain, 기존 `UserDefaults` 마이그레이션 | 같은 기기의 일반적인 삭제·재설치에서 Keychain 값을 재사용 | 1차 대응 완료 |
| Android | `SharedPreferences` | Android Auto Backup 설정·시점·제조사·복원 경로에 따라 우연히 복원될 수 있음 | 미해결 |

Android의 `SharedPreferences`는 Auto Backup 대상이지만 백업이 항상 수행되거나 복원된다는
보장이 없다. Android 공식 문서도 인증 토큰처럼 민감한 자격 증명의 재설치 복원에는 일반
파일 백업보다 Block Store 사용을 안내한다. 따라서 현재 Android 동작을 해결된 상태로
판단하지 않는다.

## Android 적용 순서

1. 앱 시작 시 `SharedPreferences`의 토큰을 먼저 읽는다.
2. 값이 없으면 Block Store에서 복구용 Refresh Token을 읽는다.
3. 복구 값이 있으면 `/auth/token/refresh`를 호출한다.
4. 성공하면 새 토큰 쌍을 `SharedPreferences`에 저장하고, 회전된 Refresh Token을 Block
   Store에도 덮어쓴다.
5. Block Store에도 값이 없을 때만 최초 사용자로 판단해 새 게스트를 생성한다.
6. 복구 값은 있으나 서버가 거부하면 새 게스트를 만들지 않고 복구 오류를 노출하며
   `auth_session_refresh_failed`를 프로덕션 Analytics에 기록한다.

Block Store도 Google Play services와 사용자의 백업 설정에 의존하므로 완전한 계정 복구
수단은 아니다. 다만 서버 API와 데이터베이스를 바꾸지 않고 현재 제약에서 재설치 손실을
줄일 수 있는 Android의 1차 대응이다.

## 장기 대안

정확한 해결책은 게스트 기록을 영구 계정이나 별도의 복구 자격 증명에 연결하는 것이다.
Apple·Google 로그인 연결 또는 서버가 발급하는 복구 키를 도입하면 Access/Refresh Token을
잃어도 같은 회원을 다시 확인할 수 있다. 이때 기록은 서버의 기존 회원 ID에 계속 귀속되고,
로컬 캐시는 인증 성공 후 서버 상태와 동기화한다.

서버 구조 변경이 어려운 현재 단계에서는 플랫폼 보존 저장소와 기존 refresh API를
사용한다. 새 게스트에게 로컬 기록을 자동 재업로드하는 방식은 채택하지 않는다.

## 참고

- [Android Auto Backup](https://developer.android.com/identity/data/autobackup)
- [Android Block Store](https://developer.android.com/identity/block-store)
- [Android Restore Credentials](https://developer.android.com/identity/sign-in/restore-credentials)

## 갱신 실패와 응답 유실 처리 보완 (PR #438)

저장소 오류 구분, 세션 소유 갱신, 제한된 재시도와 취소 처리는 [ADR 0001](adr/0001-guest-refresh-recovery.md)을 따른다. [트러블슈팅과 검증](troubleshooting/guest-refresh-response-loss.md)에 서버 의존성과 잔여 검증을 기록했다. 이 보완은 위 Android 재설치 복구 후속 작업을 구현한 것은 아니다.
