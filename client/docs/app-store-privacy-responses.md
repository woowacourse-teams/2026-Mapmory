# Mapmory App Store Connect App Privacy 응답 기준

기준: `com.mapmory.ios` 출시 빌드에서 실행되는 게스트 인증, 여행 기록·사진 업로드와
Firebase Analytics 사용자 행동 분석 기능.

이 문서는 App Store Connect의 **App Privacy** 응답을 출시 빌드와 일치시키기 위한 내부 기준이다.
Apple의 질문이나 실제 데이터 처리 방식이 바뀌면 출시 전에 다시 검토한다.

## 핵심 응답

| 질문 | 응답 기준 |
| --- | --- |
| 앱 또는 제3자 파트너가 데이터를 수집하는가 | 예 |
| 수집 데이터를 추적에 사용하는가 | 아니오 |
| 제3자 광고 또는 개발자 광고 목적으로 사용하는가 | 아니오 |
| Firebase Analytics 사용 목적 | Analytics: 기능 이용 현황 분석, 사용자 경험 개선과 신규 기능 기획 |

여기서 추적은 앱에서 수집한 데이터를 다른 회사의 앱·웹사이트 데이터와 결합해 맞춤형 광고나
광고 성과 측정에 사용하거나 데이터 브로커와 공유하는 행위를 의미한다. Firebase·GA4에서 광고
개인화, 광고 계정 연결과 데이터 공유 설정이 비활성화되어 있다는 전제에서 `아니오`로 답한다.

## 데이터 유형별 응답

| App Store 데이터 유형 | 수집 내용 | 목적 | 사용자와 연결 | 추적 |
| --- | --- | --- | --- | --- |
| Identifiers → User ID | 내부 게스트 회원 ID와 게스트 식별 정보 | App Functionality | 예 | 아니오 |
| Identifiers → Device ID | Firebase 앱 인스턴스·기기 식별 정보 | Analytics | 예 | 아니오 |
| Usage Data → Product Interaction | 앱 실행, 화면 진입, 버튼 클릭, 사진 추천, 기록 저장 결과, 필터·테마 사용 | Analytics | 예 | 아니오 |
| User Content → Photos or Videos | 이용자가 여행 기록에 첨부한 사진 원본 | App Functionality | 예 | 아니오 |
| User Content → Other User Content | 여행 제목·내용·날짜·지역·태그 | App Functionality | 예 | 아니오 |
| Location → Precise Location | 업로드 사진 원본에 포함될 수 있는 EXIF 위도·경도 | App Functionality | 예 | 아니오 |
| Location → Coarse Location | 선택한 행정구역, Firebase Analytics가 IP 등으로 산출할 수 있는 대략적인 지역 | App Functionality, Analytics | 예 | 아니오 |
| Diagnostics → Other Diagnostic Data | 요청 ID, 요청 시각, API 경로·응답 상태와 오류 코드 | App Functionality | 출시 서버 로그와 회원 연결 가능성을 확인해 결정 | 아니오 |

사진 추천을 위해 기기 안에서만 읽고 서버로 보내지 않는 사진 위치정보와 미리보기는 App Store의
`Collected`에 해당하지 않는다. 다만 사용자가 사진을 기록에 첨부하면 사진 원본과 원본에 남은 EXIF
메타데이터가 서버로 전송될 수 있으므로 위 응답에 포함한다.

## Firebase Analytics 이벤트 데이터

분석 이벤트에는 화면 이름, 이동한 탭, 지도 범위, 시·도 코드, 장소 유형, 기록 보유 여부, 사진 수,
저장 성공·실패, 작성·수정 구분, 페이지 이동 방향, 테마와 필터 유형이 포함될 수 있다.

다음 데이터는 Analytics 이벤트 파라미터로 보내지 않는다.

- 여행 기록 제목과 본문
- 사진 파일명·원본·미리보기
- 원본 GPS 위도·경도
- 내부 회원 ID와 인증 토큰
- 사용자가 작성한 태그 이름

태그 필터 이벤트는 사용자가 작성한 태그 이름 대신 `all` 또는 `custom_tag` 유형만 전송한다.

## 출시 전 콘솔 확인

- [ ] Firebase Apple 앱 Bundle ID가 `com.mapmory.ios`인가
- [ ] 제출 Archive에 해당 앱의 `GoogleService-Info.plist`가 포함됐는가
- [ ] Firebase Analytics 이벤트가 운영 Apple 앱 데이터 스트림으로 들어오는가
- [ ] Google Analytics 데이터 보유기간이 개인정보처리방침의 최대 14개월 정책과 일치하는가
- [ ] Google Ads 연결, 광고 개인화와 온디바이스 광고 전환 측정이 비활성화되어 있는가
- [ ] Analytics 데이터를 다른 제품 또는 광고 목적으로 공유하는 설정이 없는가
- [ ] Xcode Organizer의 Privacy Report와 이 문서의 데이터 유형이 일치하는가
- [ ] 공개 개인정보처리방침과 App Store Connect App Privacy 응답이 일치하는가

## 관련 자료

- [Apple App Privacy Details](https://developer.apple.com/app-store/app-privacy-details/)
- [Firebase의 App Store 데이터 공개 안내](https://firebase.google.com/docs/ios/app-store-data-collection)
- [Firebase Analytics 데이터 수집 설정](https://firebase.google.com/docs/analytics/ios/configure-data-collection)
- [Mapmory 개인정보처리방침](../../docs/privacy-policy.html)
