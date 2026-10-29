# Mapmory

Mapmory는 여행지를 지도에 표시하고 사진으로 여행의 순간을 기록하는 여행 아카이브 앱입니다. 방문한 장소를 지도에서 다시 살펴보고, 여행 기록을 모아 통계와 공유 콘텐츠로 만들 수 있습니다.

Android와 iOS 앱, 공통 Compose Multiplatform 코드, Backend를 한 저장소에서 관리합니다. 앱 클라이언트와 Backend는 독립적으로 빌드하며 HTTP API로 통신합니다.

## 앱 기능

- **지도에서 여행 기록 탐색**: 세계 지도와 대한민국 시·도 및 시·군·구 지도에서 기록을 확인합니다.
- **여행 기록 작성**: 여행지를 선택하고 촬영 위치 정보를 활용해 사진을 찾아 날짜와 함께 기록합니다. 기존 기록도 수정하거나 삭제할 수 있습니다.
- **사진과 일지 탐색**: 기록별 사진을 보고, 사진 모음이나 타임라인 형태로 여행 기록을 살펴봅니다.
- **여행 통계 확인**: 방문한 지역과 여행 기록을 여행 통계 화면에서 확인합니다.
- **여행 기록 공유**: 기록을 공유 이미지로 꾸미고, 여행 타임라인 영상도 만들 수 있습니다.

## 기술 구성

| 영역 | 기술 |
| --- | --- |
| Android, iOS 앱 | Kotlin, Compose Multiplatform |
| Backend | Java 21, Spring Boot |
| 데이터 저장 | MySQL, Amazon S3 |
| 캠페인 웹 프로토타입 | React, Vite |

## 저장소 구조

```text
2026-Mapmory/
├── client/
│   ├── androidApp/      # Android 앱 진입점과 플랫폼 코드
│   ├── iosApp/          # Xcode 앱 진입점과 플랫폼 코드
│   └── shared/          # Android, iOS 공통 UI와 도메인 코드
├── backend/             # HTTP API, 인증, 데이터베이스, 파일 저장소 연동
├── landing/             # Mapmory 소개 사이트와 별도 캠페인 웹 프로토타입
├── docs/                # 프로젝트 공통 문서와 개인정보 처리방침
└── .github/workflows/   # Android, iOS, Backend, Landing CI 및 배포 워크플로
```

`landing/travel-map-campaign`은 앱과 별도로 운영하는 웹 캠페인입니다. 캠페인 웹의 영상 생성 기능은 Mapmory 앱 기능에 포함되지 않습니다.

## 개발 시작

Android 앱은 JDK 21과 Android SDK가 필요합니다.

```bash
cd client
./gradlew :androidApp:assembleDebug
```

iOS 앱은 Xcode에서 [`client/iosApp/Mapmory.xcodeproj`](client/iosApp/Mapmory.xcodeproj)를 열어 `Mapmory` scheme으로 실행합니다. Backend와 각 플랫폼의 환경 설정은 아래 안내를 참고하세요.

```bash
cd backend
./gradlew build
```

캠페인 웹 프로토타입을 실행하려면:

```bash
cd landing/travel-map-campaign
npm ci
npm run dev
```

## 문서

- [클라이언트 안내](client/README.md)
- [클라이언트 개발 환경](client/docs/environment.md)
- [API 계약](client/docs/api-contract.md)
- [지도 데이터 기준](client/docs/map-data.md)
- [iOS 앱 설정](client/iosApp/README.md)
- [Backend 개발 안내](backend/README.md)
- [Google Play 제출 체크리스트](client/docs/play-console-submission-checklist.md)
- [App Store 제출 체크리스트](client/docs/app-store-connect-submission-checklist.md)
- [App Store 개인정보 응답 기준](client/docs/app-store-privacy-responses.md)
- [캠페인 웹 프로토타입 안내](landing/travel-map-campaign/README.md)
