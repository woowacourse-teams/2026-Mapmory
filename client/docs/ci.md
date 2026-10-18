# 클라이언트 CI

Mapmory 클라이언트의 `develop` 또는 `main`을 대상으로 하는 Pull Request가 생성·수정되면 GitHub Actions가 변경 경로를 확인합니다. 병합 후 push에서는 동일 검증을 다시 실행하지 않으며, 필요한 경우 `workflow_dispatch`로 수동 실행할 수 있습니다. 같은 PR에서 새 커밋이 올라오면 이전 PR 검증은 취소됩니다.

## PR에서 실행하는 검증

`.github/workflows/android-ci.yml`은 다음 작업을 한 번의 Gradle 실행으로 수행합니다.

1. `:shared:jvmTest` — 공통 JVM 테스트
2. `:shared:testAndroidHostTest` — Android 호스트 테스트
3. `:androidApp:lint` — Android 정적 분석
4. `:androidApp:assembleDebug` — Android Debug APK 빌드

실패한 Gradle 작업은 해당 Step 로그에서 확인할 수 있습니다.

### Android CI 실행 범위

Android 빌드는 다음 경로 중 하나가 변경된 경우에만 실행합니다.

- `.github/workflows/android-ci.yml`
- `client/androidApp/**`
- `client/shared/build.gradle.kts`
- `client/shared/src/commonMain/**`, `client/shared/src/commonTest/**`
- `client/shared/src/androidMain/**`, `client/shared/src/androidHostTest/**`, `client/shared/src/androidDeviceTest/**`
- `client/shared/src/jvmMain/**`, `client/shared/src/jvmTest/**`
- `client/gradle/**`
- `client/build.gradle.kts`
- `client/settings.gradle.kts`
- `client/gradle.properties`
- `client/gradlew`, `client/gradlew.bat`

백엔드나 랜딩 페이지만 변경한 PR에서도 필수 체크가 대기 상태로 남지 않도록 workflow 자체는 실행하고, `Detect Android changes` 결과에 따라 `Test and build Android` job을 건너뜁니다. `workflow_dispatch`로 수동 실행한 경우에는 변경 경로와 관계없이 전체 Android 검증을 실행합니다.

## 로컬에서 동일하게 실행

```bash
cd client
./gradlew :shared:jvmTest :shared:testAndroidHostTest :androidApp:lint :androidApp:assembleDebug --no-daemon
```

Windows에서는 `./gradlew` 대신 `gradlew.bat`을 사용합니다.

```bat
cd client
gradlew.bat :shared:jvmTest :shared:testAndroidHostTest :androidApp:lint :androidApp:assembleDebug --no-daemon
```

## 실패 시 대응

1. PR의 **Checks** 탭에서 실패한 Step을 확인합니다.
2. 로컬에서 해당 Gradle 명령을 단독 실행해 오류를 재현합니다.
3. 오류를 수정하고 커밋을 추가하면 PR 검증이 다시 실행됩니다.
4. 모든 검증이 성공한 뒤 리뷰와 병합을 진행합니다.

## 병합 보호 규칙

저장소 관리자는 GitHub 저장소의 **Settings → Branches → Branch protection rules**에서 `develop`, `main`에 다음 규칙을 적용합니다.

- Pull request required
- Require status checks to pass before merging
- Required checks:
  - `Android CI / Test and build Android`
  - `iOS CI / Build iOS Simulator`
- Require branches to be up to date before merging

브랜치 보호 규칙은 저장소 설정이므로 workflow 파일을 커밋하는 것만으로 자동 적용되지는 않습니다.

## 이번 CI에서 제외한 검증

- `:shared:androidConnectedCheck`: 실제 Android 기기 또는 에뮬레이터가 필요하므로 PR마다 실행하지 않고 로컬 수동 검증으로 둡니다.
- iOS 실제 기기 빌드·Archive: 배포 서명과 Apple Developer 계정이 필요하므로 별도 작업으로 둡니다.

## iOS 컴파일 CI

`.github/workflows/ios-ci.yml`은 `develop`, `main` 대상 PR에서 iOS Simulator용 Debug 빌드를 실행합니다. 병합 후 push에서는 다시 실행하지 않습니다.

- Runner: `macos-26`
- Xcode scheme: `Mapmory`
- 프로젝트: `client/iosApp/Mapmory.xcodeproj`
- 서명: `CODE_SIGNING_ALLOWED=NO`, `CODE_SIGNING_REQUIRED=NO`
- 목적: 실제 배포가 아니라 Swift 코드와 Compose Multiplatform framework 연결 상태를 컴파일 단계에서 확인

iOS 빌드는 다음 경로 중 하나가 변경된 경우에만 실행합니다.

- `.github/workflows/ios-ci.yml`
- `client/iosApp/**`
- `client/shared/build.gradle.kts`
- `client/shared/src/commonMain/**`, `client/shared/src/commonTest/**`
- `client/shared/src/iosMain/**`, `client/shared/src/iosTest/**`
- `client/gradle/**`
- `client/build.gradle.kts`
- `client/settings.gradle.kts`
- `client/gradle.properties`
- `client/gradlew`, `client/gradlew.bat`

백엔드, 랜딩 또는 Android 앱 전용 변경에서도 필수 체크가 대기 상태로 남지 않도록 workflow 자체는 실행하고, `Detect iOS changes` 결과에 따라 `Build iOS Simulator` job을 건너뜁니다. `workflow_dispatch`로 수동 실행한 경우에는 변경 경로와 관계없이 iOS Simulator 빌드를 실행합니다.

수동 계측 테스트는 Android 기기 또는 에뮬레이터를 연결한 뒤 다음 명령으로 실행합니다.

```bash
cd client
./gradlew :shared:androidConnectedCheck
```
