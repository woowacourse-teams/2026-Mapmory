# 카카오 Android·iOS 설정

## 카카오 콘솔 반영 (2026-10-26)

앱 `mapmory` (1547585)의 기본 네이티브 앱 키를 사용한다. 카카오 로그인과 OpenID Connect가 모두 ON인 것을 확인했다. 사용자 승인 후 플랫폼 설정을 저장하고 페이지를 다시 열어 값과 수정 일시를 확인했다.

- Android 패키지: `com.mapmory.android`
- iOS Debug/Release 번들: `com.mapmory.ios`
- App Store ID: `6807056166`
- 네이티브 앱 키: `a5125976d5b1e29e6319da61940dbfd7`
- 콜백: `kakaoa5125976d5b1e29e6319da61940dbfd7://oauth`

### 등록된 Android 키 해시

| 인증서 | 키 해시 |
| --- | --- |
| 현재 Mac debug | `MDTAtTlWRfWrZ+IkCmi5F0P4UE4=` |
| Play deployment_cert.der | `SEkrhGgNKxO+ZHpQEGskut4AGrU=` |
| Play hybrid_classical_cert.der | `PXfW293O/uHIM7Tvq18ogiAikl4=` |
| Play hybrid_pqc_cert.der | `kLhwB9a21OtL9Gho9EwBjt7U7Tw=` |

Play 앱 서명 화면에서 받은 공개 인증서 묶음으로 계산했다. deployment_cert.der의 SHA-256은 화면의 `05:58:42:F7:F6:B8:63:99:D1:55:C5:AB:AE:31:B6:32:29:B4:07:AA:79:AF:B6:74:7C:DC:1F:4F:5E:CF:3B:E8`과 일치한다. 업로드 키의 해시를 Play 설치본용 키 해시로 대신 사용하지 않는다.

## 앱 설정

Android는 환경변수 `MAPMORY_KAKAO_NATIVE_APP_KEY`, local.properties의 동일 값, 코드의 공개 기본 식별자 순으로 사용한다. 모든 빌드 유형에 동일한 키와 콜백 스킴을 반영한다.

iOS는 `SocialLogin.xcconfig`를 Debug/Release/Internal의 기본 설정으로 연결했다. Info.plist의 키와 URL scheme에 빌드 설정이 확장된다. Release 산출물에서 키, 스킴, 번들 ID를 확인했다. 배포 바이너리에 포함되는 네이티브 앱 키는 공개 앱 식별자이며 어드민 키/Client Secret/사용자 토큰과 다르다. 후자의 비밀값은 추가하지 않는다.

Internal은 Android `com.mapmory.android.internal`, iOS `com.mapmory.ios.internal`로 별도 식별자를 사용한다. 이번 콘솔 등록 범위는 기본 앱의 개발·스토어 배포이다. Internal 로그인은 별도 플랫폼 등록/네이티브 키 정책을 정한 뒤 검증해야 한다.

## 백엔드·출시 확인

- 앱은 backend-develop 규격의 `POST /api/v1/auth/login/kakao`, body `idToken`을 사용한다.
- 개발·운영 서버 각각 `KAKAO_CLIENT_IDS`에 위 네이티브 앱 키를 허용해야 한다. 서버 구현 존재만으로 배포 환경변수 설정을 확인할 수는 없다.
- 2026-10-26 fetch한 backend-release 소스의 KakaoLoginRequest는 아직 `kakaoAccessToken`이다. backend-develop은 `idToken`이다. 실제 운영 배포 규격은 별도로 확인하고 앱 출시 전에 맞춰야 한다. 이 작업에서 서버 배포/환경변수는 수정하지 않았다.
- Android assembleDebug/assembleRelease 성공. iOS Release 시뮬레이터 전체 빌드 성공, 최종 Info.plist의 키·URL scheme·번들·운영 API 주소 확인.
- iOS 스토어 서명 Archive, 실제 iPhone 로그인, Play 설치본 인증 성공은 아직 검증하지 않았다. 스토어 업로드/출시는 하지 않았다.
- 연결된 Android 휴대폰에 최종 APK 설치를 시도했으나 기기가 분리되어 설치하지 못했다. 재연결 후 실제 카카오 인증·서버 교환·회원 확인·앱 재시작 유지 검증이 남아 있다.

## 공식 문서

- https://developers.kakao.com/docs/en/kakaologin/prerequisite
- https://developers.kakao.com/docs/en/ios/getting-started
- https://developers.kakao.com/docs/en/android/getting-started
