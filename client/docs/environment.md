# 클라이언트 개발 환경

## 기준 버전

| 항목 | 기준 |
| --- | --- |
| Kotlin | 2.4.10 |
| Compose Multiplatform | 1.11.1 |
| Android Gradle Plugin | 9.1.0 |
| Gradle | 9.5.0 (Wrapper로 고정) |
| JDK | 21 |
| Android compileSdk | 36 |
| Android targetSdk | 36 |
| Android minSdk | 28 |
| iOS Deployment Target | 16.0 |
| Xcode | 26.4 계열 |

Kotlin 2.4.10의 공식 KMP 호환 범위에 맞추기 위해 AGP 9.1.0을 사용합니다.

## 프로젝트 경계

- `androidApp`은 Android 진입점과 Android 전용 코드만 관리합니다.
- `iosApp`은 Xcode 진입점과 iOS 전용 코드만 관리합니다.
- `shared`는 Android와 iOS에서 함께 사용하는 코드만 관리합니다.
- Backend 소스와 의존성은 클라이언트 Gradle 프로젝트에 포함하지 않습니다.

## 지도

현재는 외부 지도 SDK를 사용하지 않는다. 지도 기능은 행정구역 GeoJSON과
`Location.regionCode`를 매칭하며, 대한민국 시·도는 ISO 3166-2, 시·군·구는
행정표준코드를 사용한다.

## Mapmory API Base URL

빌드 종류에 따라 API 주소를 분리한다.

| 플랫폼 | 테스트 빌드 | 출시 빌드 |
| --- | --- | --- |
| Android | `debug`: `https://dev-api.map-mory.com/api/v1` | `release`: `https://api.map-mory.com/api/v1` |
| iOS | `Debug`: `https://dev-api.map-mory.com/api/v1` | `Release`: `https://api.map-mory.com/api/v1` |

Android에서 로컬 Backend를 연결할 때만 `client/local.properties`에 Debug 전용 주소를 추가한다.

```properties
MAPMORY_DEBUG_API_BASE_URL=http://10.0.2.2:8080/api/v1
```

이 값은 Android Debug 빌드에만 적용된다. Release 빌드는 `local.properties`와 무관하게 운영 API를 사용한다.
Android 에뮬레이터에서 호스트 PC의 `localhost`는 `10.0.2.2`로 접근한다.

## 현재 검증 상태

- JDK 21: 로컬 확인 완료
- Gradle Wrapper: `./gradlew --version`으로 9.5.0 확인
- Android SDK 36: `/Users/chohs4164/Library/Android/sdk/platforms/android-36` 확인
- Xcode 전체 설치: 현재 Command Line Tools만 활성화되어 있음
- Android Debug 빌드: `./gradlew :androidApp:assembleDebug` 성공
- iOS 실제 빌드: Xcode 전체 설치 후 수행
