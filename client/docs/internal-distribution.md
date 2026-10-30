# 내부 테스트 앱 배포

Mapmory 내부 테스트 앱은 운영 앱의 내부 테스트 트랙이 아니라 별도로 설치되는 앱이다.
운영 패키지의 내부 테스트 트랙에서 설치한 빌드는 분석 이벤트가 운영 앱의 이벤트로
집계되므로, 운영 앱과 내부 테스트 앱을 의도적으로 분리한다.

| 플랫폼 | 운영 앱 식별자 | 내부 테스트 앱 식별자 |
| --- | --- | --- |
| Android | `com.mapmory.android` | `com.mapmory.android.internal` |
| iOS | `com.mapmory.ios` | `com.mapmory.ios.internal` |

내부 테스트 앱은 `https://dev-api.map-mory.com/api/v1`을 사용하며 앱 이름은
`Mapmory Internal`이다. 운영 앱과 내부 테스트 앱을 한 기기에 동시에 설치할 수 있다.

## 분석 이벤트 차단

내부 테스트 빌드에는 다음과 같은 독립적인 차단 장치가 있다.

- 패키징된 매니페스트 또는 `Info.plist`에서 Firebase Analytics를 비활성화한다.
- Android Firebase 로거는 `.internal`로 끝나는 패키지에서 초기화되지 않는다.
- Android 내부 테스트 매니페스트에서 Firebase 자동 초기화 프로바이더를 제거한다.
- Android 내부 테스트 빌드에서는 Google Services 플러그인을 실행하지 않아 운영용
  `google_app_id`가 실수로 포함되지 않는다.
- iOS는 `INTERNAL` 컴파일 조건에서 `FirebaseApp.configure()`를 호출하지 않으며,
  앱 번들에서 `GoogleService-Info.plist`를 제외한다.

이 차단 장치는 바이너리에 포함되므로 내부 테스트 앱을 삭제한 뒤 다시 설치해도
분석 이벤트 수집이 활성화되지 않는다. 운영 앱은 변경되지 않으며 기존처럼 분석
이벤트를 수집한다.

## 빌드 방법

Android 앱 번들:

```sh
sh ./scripts/build-internal-android.sh
```

생성된 번들은
`androidApp/build/outputs/bundle/internal/androidApp-internal.aab`에 저장된다.
스크립트는 전용 `.signing/mapmory-internal-upload.jks` 키를 사용하고, macOS 키체인의
`com.mapmory.android.internal.upload-key` 서비스에서 비밀번호를 읽는다. 키 저장소는
별도로 백업하고 저장소에는 커밋하지 않는다.

iOS:

1. Xcode에서 공유된 `Mapmory-Internal` 스킴을 선택한다.
2. **Any iOS Device (arm64)**를 선택한다.
3. 번들 식별자가 `com.mapmory.ios.internal`인 App Store Connect 앱에만 아카이브를
   배포한다.

## 스토어 설정

- Play Console에 `com.mapmory.android.internal`용 앱을 별도로 만들고 내부 테스트
  트랙만 사용한다. 이 패키지는 Firebase에 추가하지 않는다.
- App Store Connect에 `com.mapmory.ios.internal`용 앱을 별도로 만들고 TestFlight
  내부 테스트를 사용한다. 이 번들 식별자는 Firebase 앱으로 등록하지 않는다.
- 내부 테스트 산출물을 운영 Mapmory 스토어 항목에 업로드하지 않는다. 앱 식별자가
  다르므로 일반적으로 스토어에서 잘못된 업로드를 거부한다.

테스터 추가는 접근 권한을 변경하는 작업이다. 가능하면 팀의 기존 테스터 그룹을
사용하고, 권한을 부여하기 전에 그룹 구성원을 확인한다.
