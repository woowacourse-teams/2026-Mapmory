# Mapmory App Store Connect 제출 체크리스트

기준: App Store용 Bundle ID `com.mapmory.ios`로 만드는 iOS 출시 빌드.

## 1. 소스와 버전

- [ ] 출시 기준 브랜치와 커밋을 기록했는가
- [ ] 모든 필수 CI가 성공했는가
- [ ] `MARKETING_VERSION`이 App Store Connect 버전과 일치하는가
- [ ] `CURRENT_PROJECT_VERSION`이 이전에 업로드한 빌드보다 큰가
## 2. Firebase Analytics

- [ ] Firebase Console에 Bundle ID `com.mapmory.ios` Apple 앱이 등록됐는가
- [ ] 해당 앱에서 내려받은 `GoogleService-Info.plist`를 사용했는가
- [ ] plist의 `BUNDLE_ID`와 Xcode Release의 `PRODUCT_BUNDLE_IDENTIFIER`가 일치하는가
- [ ] Release 실기기에서 Firebase 초기화 오류가 없는가
- [ ] DebugView 또는 운영 보고서에서 핵심 이벤트 수신을 확인했는가
- [ ] Google Analytics 데이터 보유기간이 최대 14개월 정책과 일치하는가

설정 파일의 Bundle ID 문자열만 직접 바꾸면 Firebase 앱 등록 정보는 바뀌지 않는다. 반드시
Firebase Console에서 운영 Bundle ID로 등록된 앱의 파일을 사용한다.

## 3. 출시 빌드 검증

- [ ] Xcode 26 이상과 iOS 26 SDK 이상으로 Archive했는가
- [ ] Signing Team, App ID와 배포 인증서가 올바른가
- [ ] Archive Validation이 성공했는가
- [ ] Organizer의 Privacy Report에서 예상하지 않은 SDK·수집·추적 도메인이 없는가
- [ ] TestFlight 실기기에서 앱 설치와 실행을 확인했는가
- [ ] 게스트 인증, 기록 생성·조회·수정·삭제가 운영 서버에서 동작하는가
- [ ] 사진 선택·업로드·조회가 동작하는가
- [ ] 사진 접근 전체 허용·제한 허용·거부 상태를 각각 확인했는가
- [ ] 태그 필터와 Firebase Analytics 이벤트가 의도대로 동작하는가
- [ ] 롤백된 백엔드와 최신 앱의 태그·통계·사진 API가 호환되는가

## 4. App Store Connect 메타데이터

- [ ] 새 빌드가 처리 완료 상태인가
- [ ] 제출할 앱 버전에 새 빌드를 선택했는가
- [ ] `새로운 기능` 문구를 작성했는가
- [ ] 앱 설명, 키워드, 지원 URL과 마케팅 URL이 현재 기능과 맞는가
- [ ] 개인정보처리방침 URL이 HTTPS로 로그인 없이 열리는가
- [ ] 스크린샷이 현재 UI와 크게 다르지 않은가
- [ ] 2026년 연령 등급 질문을 모두 답했는가
- [ ] 수출 규정 질문이 `ITSAppUsesNonExemptEncryption=false` 설정과 일치하는가
- [ ] 출시 방식을 자동, 수동 또는 단계적 출시 중에서 결정했는가

## 5. App Privacy

[`app-store-privacy-responses.md`](./app-store-privacy-responses.md)를 기준으로 다음을 확인한다.

- [ ] User ID
- [ ] Device ID
- [ ] Product Interaction
- [ ] Photos or Videos
- [ ] Other User Content의 앱 기능 사용과 태그 이름의 Analytics 미전송
- [ ] Precise Location과 Coarse Location
- [ ] Other Diagnostic Data의 실제 서버 저장·연결 여부
- [ ] 각 항목의 목적, 사용자 연결 여부와 Tracking 여부
- [ ] 광고·추적 없음 응답이 Firebase·GA4 실제 설정과 일치하는가

## 6. App Review Information

- [ ] 심사 담당자 이름·이메일·전화번호가 유효한가
- [ ] 앱이 자동 게스트 인증을 사용하므로 별도 계정이 필요 없다고 설명했는가
- [ ] 리뷰 기간 동안 운영 API와 사진 저장소를 사용할 수 있는가
- [ ] 사진 권한을 거부해도 나머지 기능을 사용할 수 있다고 설명했는가
- [ ] 심사 메모에 테스트 경로를 적었는가

심사 메모 예시:

```text
Mapmory automatically creates a guest session, so no review account is required.

To test the main flow, select a region on the map, create a travel record, and optionally attach photos.
Photo Library access is optional. Full access is required only for location-based photo recommendations;
the rest of the app remains available when access is limited or denied.

Firebase Analytics is used only to understand feature usage and improve the service. The app does not
serve ads or use Analytics data for cross-app tracking or advertising personalization.
```

## 7. 제출 직전

- [ ] 선택한 빌드 번호와 실제 Archive의 빌드 번호가 같은가
- [ ] 개인정보처리방침 시행일과 공개 페이지 내용이 최신인가
- [ ] 심사 제출 항목에 누락 또는 경고가 없는가
- [ ] 제출 후 App Review 상태와 Resolution Center 메시지를 확인할 담당자가 정해졌는가

## 관련 자료

- [Apple 빌드 업로드](https://developer.apple.com/help/app-store-connect/manage-builds/upload-builds)
- [Apple 제출 빌드 선택](https://developer.apple.com/help/app-store-connect/manage-builds/choose-a-build-to-submit)
- [Apple App Privacy 관리](https://developer.apple.com/help/app-store-connect/manage-app-information/manage-app-privacy)
- [Apple App Review 제출 개요](https://developer.apple.com/help/app-store-connect/manage-submissions-to-app-review/overview-of-submitting-for-review/)
