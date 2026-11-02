# 애플 회원 탈퇴 배포 및 장애 대응

## 배포 조건

이 변경은 기존 `DELETE /api/v1/members/me`에서 활성 애플 회원을 `400 APPLE_REAUTHENTICATION_REQUIRED`로 거부한다. 서버만 배포하면 기존 API를 호출하는 iOS 앱에서 탈퇴할 수 없다.

- 아직 외부 사용자가 없다면 개발 환경에서 아래 실제 계정 검증을 마친 뒤 서버와 대응 iOS 버전을 함께 출시한다.
- 이미 사용자가 있다면 새 버전 출시만으로 구버전 설치가 사라지지 않는다. 전용 탈퇴 API를 쓰는 앱을 배포하고, 구버전에서 업데이트 안내와 실제 업데이트 경로가 동작하는지 먼저 확인해야 한다. 이 PR에는 최소 지원 버전 강제나 호환성 전환 기능이 없다. 이 조건을 충족하지 못하면 운영 배포를 보류하고 전환 방식을 별도 구현한다.
- iOS는 탈퇴 직전 Sign in with Apple 재인증 결과를 받아 전용 API로 보낸다. `204`에서만 탈퇴 완료 처리와 Mapmory 토큰 삭제를 한다. `503 APPLE_WITHDRAWAL_INCOMPLETE`에서는 토큰을 유지하고 새 재인증으로 재시도하도록 안내한다.

## EC2 설정

개발·운영 환경 각각 배포 전에 `/etc/mapmory.env`를 편집한다. 파일에는 `export` 없이 다음 키를 설정한다. 아래는 값의 의미를 보여 주는 예시이며 그대로 복사하지 않는다.

```text
APPLE_CLIENT_IDS=허용할_iOS_Bundle_ID
APPLE_TEAM_ID=Apple_개발자_Team_ID
APPLE_KEY_ID=발급한_Sign_in_with_Apple_Key_ID
APPLE_PRIVATE_KEY=p8의_BEGIN_END_줄을_제외한_Base64_본문을_한_줄로_연결한_값
```

운영·Internal 앱을 함께 허용한다면 실제 Bundle ID를 쉼표로 구분한다. Key ID는 사용자 ID가 아니며, 해당 팀의 Sign in with Apple 키 ID다. `.p8`은 서버 서명용 개인키로 저장소·PR·로그에 남기지 않는다. 키 본문을 한 줄로 쓰면 systemd 환경 파일의 줄바꿈·따옴표 처리를 피할 수 있다.

```bash
sudo nano /etc/mapmory.env
sudo systemctl restart mapmory
sudo systemctl status mapmory --no-pager
```

새 백엔드 배포 후에도 기동 상태를 확인한다. 세 서명 설정 중 하나라도 비어 있으면 로그인은 가능해도 탈퇴는 `503 APPLE_UNAVAILABLE`다. 세 값이 있고 개인키 형식이 잘못되면 기동에 실패한다. Team ID·Key ID·앱 연결 설정이 실제로 맞는지는 기동 성공만으로 검증되지 않으므로 아래 확인까지 필요하다.

## 실제 계정으로 확인할 항목

자동 테스트는 애플 HTTP 응답을 대체하므로, 개발 환경의 실제 iOS 앱과 삭제해도 되는 테스트 계정으로 다음을 확인한다.

1. 애플 신규 가입과 게스트 → 애플 승격 계정 모두 전용 탈퇴 경로로 진입한다.
2. 같은 애플 계정으로 재인증하고 `identityToken`·`authorizationCode`를 즉시 보내면 `204`가 반환된다. Apple 계정 설정에서 앱 연결이 해제됐는지 확인한다.
3. Mapmory 회원 개인정보가 탈퇴 상태로 바뀌고 Refresh Token으로 갱신할 수 없는지 확인한다. 이후 다시 가입하면 새 회원이 되는지도 확인한다.
4. 재인증 취소, 다른 애플 계정, 만료·재사용 code, 네트워크 실패에서는 탈퇴 완료로 표시하지 않는다.
5. 완료 응답 유실을 가정해 유효한 기존 Mapmory Access Token과 필수 본문으로 다시 요청하면 `204`다. 이미 만료된 Access Token은 재요청할 수 없다.
6. `APPLE_WITHDRAWAL_INCOMPLETE`를 받은 앱이 로그아웃하거나 같은 code를 반복 전송하지 않고, 새 재인증을 안내하는지 확인한다.

## 애플 철회 성공 후 DB 실패

외부 애플 철회와 Mapmory DB 커밋은 하나의 트랜잭션이 아니다. 로컬 트랜잭션 실패를 감지하면 서버는 회원 변경과 Mapmory refresh 폐기를 롤백하고 `503 APPLE_WITHDRAWAL_INCOMPLETE`를 반환한다.

운영 JSON 로그의 `event=APPLE_WITHDRAWAL_LOCAL_FAILED`를 찾는다. `memberId`, 응답 헤더 `X-Request-Id`와 같은 `requestId`, 실패 단계 `stage=MEMBER_WITHDRAWAL`, 예외 종류 `failureType`이 포함된다. 기존 ERROR 수집·알림이 이 로그를 수집하는지 배포 시 확인한다.

1. 요청 ID와 회원 ID로 발생 시각을 찾고 DB 연결·트랜잭션 장애를 복구한다. 개인정보·토큰·code·개인키를 문의나 로그에 첨부하지 않는다.
2. 회원의 `withdrawn_at`과 Refresh Token 폐기 상태를 확인한다. 커밋 응답 유실처럼 서버가 성공 여부를 확정할 수 없는 경우도 있으므로 로그만으로 DB 상태를 단정하지 않는다.
3. 아직 활성 회원이면 사용자가 새 애플 재인증 결과로 다시 요청하도록 안내한다. 기존 Mapmory 세션은 유지하며, Access Token 만료 시 정상 갱신 흐름을 따른다. 이미 탈퇴했다면 유효한 기존 Access Token으로 재요청할 때 `204`다.
4. 사용자 재시도가 불가능하거나 실패가 반복되면 운영자가 해당 사례를 추적해 후속 조치를 결정한다. 사용자별 애플 토큰을 보관하지 않으므로 운영자가 예전 code로 철회를 재실행하거나 백그라운드 작업으로 자동 복구할 수 없다.

철회 직후 프로세스가 종료되면 전용 실패 로그도 남지 않을 수 있다. 사용자가 재시도하지 않으면 애플 연결 해제와 Mapmory 활성 상태의 불일치가 계속될 수 있다. 이 PR의 보장 범위는 실패 응답·추적 로그·재인증 재시도이며, 모든 실패를 자동 감지하거나 자동으로 탈퇴를 완료하는 것은 아니다.
