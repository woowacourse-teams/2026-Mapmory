# Mapmory

여행을 지도에 남기는 서비스 Mapmory의 통합 저장소입니다.

이 저장소 하나에서 Android, iOS, Compose Multiplatform 공통 코드, Backend를 관리합니다. 저장소는 하나지만 각 영역의 소스·빌드·의존성·배포 경계는 분리합니다.

## 프로젝트 구조

```text
2026-Mapmory/
├── client/
│   ├── androidApp/     # Android 앱 진입점과 Android 전용 코드
│   ├── iosApp/         # Xcode 앱 진입점과 iOS 전용 코드
│   └── shared/         # Compose Multiplatform 공통 UI·상태·도메인 코드
│
├── backend/            # API 서버·인증·DB·파일 저장소 연동
├── docs/               # 프로젝트 공통 문서와 API 명세
└── .github/            # 영역별 CI/CD
```

## 클라이언트와 서버의 경계

클라이언트와 서버 코드를 같은 디렉터리에 섞지 않습니다.

```text
client/*  ── HTTP/JSON API ──>  backend/*
```

- 저장소 루트에는 공통 Gradle 설정을 두지 않습니다.
- `client`와 `backend`가 각각 자체 `settings.gradle.kts`, Gradle Wrapper와 의존성 버전을 관리합니다.
- Android/iOS 코드가 Backend 소스 파일을 직접 import하지 않습니다.
- Backend는 API 계약을 통해서만 클라이언트와 통신합니다.
- API 요청·응답 모델은 우선 각 영역에서 별도로 관리하고, 필요할 때 OpenAPI 또는 명세 문서로 동기화합니다.
- Backend 비밀정보와 클라이언트 서명키는 각각 서버 Secret 저장소와 팀 비밀번호 관리자에 보관합니다.

따라서 저장소는 하나여도 코드가 자동으로 꼬이지 않습니다. 물리적 디렉터리, 빌드 설정, 의존성, API 경계를 분리하면 됩니다.

## 실행 위치

```bash
# Android / 공통 클라이언트
cd client
./gradlew :androidApp:assembleDebug

# Backend
cd backend
# backend의 별도 실행 명령 사용
```

iOS는 `client/iosApp`을 Xcode에서 열어 실행합니다.

## 문서

- [클라이언트 안내](client/README.md)
- [클라이언트 환경](client/docs/environment.md)
- [iOS 환경](client/iosApp/README.md)
- [Backend 안내](backend/README.md)

## 팀 운영 원칙

- 변경된 영역의 담당자가 해당 영역을 리뷰합니다.
- 클라이언트 변경은 `client/**`, 서버 변경은 `backend/**` 아래에 둡니다.
- 비밀정보·서명키·개인 환경 경로는 커밋하지 않습니다.
- CI는 변경된 영역에 필요한 작업만 실행합니다.
