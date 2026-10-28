# iOS 카카오톡 인증 중 직접 복귀

## 원인

사용자가 iPhone에서 카카오톡 인증을 진행하지 않고 맵모리로 돌아오면 로딩이 끝나지 않는다고 보고했다. 현재 Kakao SDK의 카카오톡 인증은 복귀 URL을 handleOpenUrl로 전달해야 completion을 실행한다. 앱 전환으로 직접 돌아오는 경로는 URL이 없으므로 completion이 없을 수 있다. 기존 앱은 SDK 결과만 기다려 제공자 인증의 busy 상태를 해제하지 못했다.

## 처리

- 카카오톡 인증을 시작한 경우에만 해당 UIWindowScene의 백그라운드 진입과 활성화를 관찰한다.
- 실제 앱 이탈 후 직접 복귀하면 1초 동안 URL 전달을 기다린다. 활성화와 onOpenURL의 전달 순서 차이를 흡수하기 위한 유예이며 SDK 네트워크 시간 제한이 아니다.
- SDK가 복귀 URL을 수락하면 즉시 복귀 취소 검사를 해제한다. 이후 느린 토큰 교환은 정상 SDK 콜백을 기다린다.
- URL 없이 앱이 활성 상태에 남아 있으면 기존 카카오톡 URL 핸들러를 비우고 SocialLoginCancelled를 한 번 전달한다. 기존 공통 취소 처리로 로딩을 끝내고 버튼을 다시 활성화하며 게스트 세션을 유지한다. 서버 로그인 요청과 login/app_error 이벤트를 만들지 않는다.
- 시도별 UUID로 완료된 시도의 늦은 SDK 콜백이나 예약된 검사가 다음 로그인을 완료·취소하지 못하게 한다.
- 종료 시 observer와 예약 검사를 제거한다. 웹 로그인은 기존 ASWebAuthenticationSession의 취소 콜백을 사용한다.

## 검증

2026-10-27: 카카오톡 복귀 상태·늦은 콜백 검증 13개, 기존 Google 설정 검증 16개, Xcode Debug iOS Simulator 전체 앱 빌드 통과. 실제 iPhone에서의 카카오톡 복귀 재현 확인은 아직 수행하지 않았다.

```sh
swiftc iosApp/IosSocialLoginLauncher.swift iosApp/tests/KakaoTalkReturnStateTests.swift -o /tmp/mapmory-kakao-return-tests
/tmp/mapmory-kakao-return-tests
```

직접 복귀, 초기 활성화 무시, URL과 활성화의 순서 차이, 느린 교환 유지, 중복 완료, 이전 시도의 콜백·예약 검사, 웹 인증 제외를 확인한다. 실제 iPhone 카카오톡 앱 전환과 OS의 URL 전달 지연은 별도 실기기 확인 대상이다. 확인 순서는 카카오톡 진입 → 인증하지 않고 맵모리로 복귀 → 로딩 종료 → 재시도 → 정상 로그인이다.
