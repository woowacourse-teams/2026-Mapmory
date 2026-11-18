# 개발 서버 인증과 알림 이동 재현

`internal` 빌드의 `인증 재현`, `알림 재현` 버튼으로 실제 개발 서버 응답과 알림 진입을 확인합니다. 기본 앱과 다른 패키지인 `com.mapmory.android.internal`을 사용합니다. 두 도구는 API 주소가 정확히 `https://dev-api.map-mory.com/api/v1`일 때만 열리며, `debug`와 `release`에는 동작하지 않는 구현만 들어갑니다.

## 실행

기존 프로젝트의 Internal 서명 설정을 사용해 `client`에서 `./gradlew :androidApp:assembleInternal`을 실행하고, 폐기 가능한 개발 테스트 계정으로 로그인합니다. 서명 키나 계정 정보는 저장소에 넣지 않습니다. `MAPMORY_DEBUG_API_BASE_URL`을 바꿨다면 `client/local.properties`의 주소를 먼저 확인합니다.

## 인증

- `세션 준비 / 상태 조회`로 현재 세션을 확인합니다. 표시되는 만료 시각은 JWT를 읽은 값이며 토큰을 수정하지 않습니다.
- 자연 만료 후 `/me 1회` 또는 `동일 토큰으로 /me 3회 동시`를 실행해 원래 401, 갱신 전송과 응답, 재요청 결과를 확인합니다. 로그에는 토큰과 응답 본문을 기록하지 않습니다.
- 실제 오프라인 시연은 `다음 실제 갱신 직전 대기`를 켜고 요청합니다. `PAUSED` 상태에서 기기의 Wi-Fi와 모바일 데이터를 끄고 `갱신 재개`를 누릅니다. 대기는 최대 90초이며 취소하면 전송하지 않습니다. 시연 후 네트워크를 복구합니다.
- `갱신만 전송 전 모의 차단`은 별도의 인위적인 통신 오류 도구입니다. 이를 실제 기기 오프라인 증거로 사용하지 않습니다.
- 갱신 거절은 `폐기용 세션` 확인 문구를 입력한 뒤 서버 로그아웃으로 만듭니다. **현재 개발 세션을 실제로 폐기**하며, 로컬 토큰은 유지해 이후 갱신 거절을 관찰합니다. 개인 계정에는 실행하지 않습니다.
- 도구의 `/me` 실패 뒤 복구 안내는 세션의 `restore()`와 `requestRecoveryPrompt()`를 호출합니다. 이 결과만으로 기록 저장 요청의 자동 재시도나 모든 입력의 보존을 증명하지 않습니다.

## 로컬 알림 이동

현재 계정의 최근 개발 기록 ID를 조회하거나, 접근 가능한 기존 기록 ID를 입력합니다. 시스템 알림 권한과 사진 완료 알림 설정을 켜고 30초 예약 후 홈 화면으로 이동합니다. 기록을 새로 저장하거나 수정하지 않습니다.

- 실행 중 진입: 실제 알림을 눌러 기록 상세가 열리는지, 뒤로가기가 기존 화면으로 돌아가는지 확인합니다.
- 프로세스 종료 후 진입: 알림이 **생긴 다음** 앱 프로세스를 종료하고 실제 알림을 누릅니다. 예약 중 프로세스가 종료되면 새 알림은 생성되지 않습니다. 일반 프로세스 종료와 설정의 강제 중지를 구분합니다.
- 예약 중 계정이 바뀌거나 앱이 전경에 있으면 알림을 만들지 않습니다. 대화상자를 닫아도 예약은 유지되며 예약 취소 버튼으로 취소할 수 있습니다.

이 도구는 로컬 알림의 목적지와 뒤로가기 검증입니다. 실제 사진 업로드 완료, 서버 FCM 수신, 영상 완료 알림의 증거로 사용하지 않습니다. 녹화를 공유하기 전에는 계정 선택창, 이메일, 전화번호와 다른 앱의 알림을 가립니다.

## 자동 검증

- 공용 HTTP 설정과 인증 회귀: `./gradlew :shared:jvmTest --tests com.mapmory.shared.app.HttpClientConfigurationTest --tests com.mapmory.shared.data.remote.AuthRetryTest`
- Internal 정책과 대기 해제: 연결한 테스트 기기에서 `./gradlew -I gradle/internal-tests.init.gradle :androidApp:connectedInternalAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.mapmory.android.AuthReproductionPolicyTest,com.mapmory.android.NotificationReproductionPolicyTest`. 체크인한 init script가 이 실행에만 instrumentation 대상을 `internal`로 바꿉니다. 기본 debug 테스트 대상은 유지합니다.
- 알림 계정 복원: `NotificationAccountRestoreTest`는 인메모리 기록으로 계정 복원 후 상세 이동과 기존 설정 화면 복귀를 검사합니다. 개발 서버나 FCM은 사용하지 않습니다. 기존 Espresso의 API 36 이상 입력 초기화 문제가 있어 API 35에서 검증했습니다. 기본 debug 패키지는 운영 앱과 같으므로 별도 테스트 기기 또는 격리한 applicationId를 사용합니다.
