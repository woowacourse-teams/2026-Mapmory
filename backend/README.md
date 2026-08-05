# Mapmory Backend

Mapmory API 서버, 인증, 데이터베이스, 파일 저장소 연동을 관리할 독립 빌드 프로젝트입니다.

Backend 기술 스택이 결정되면 Gradle Wrapper, `settings.gradle.kts`, `build.gradle.kts` 또는 해당 빌드 도구 파일을 이 디렉터리 안에만 둡니다.

```text
backend/
├── src/
├── gradle/                 # Gradle을 선택한 경우 Backend 전용 버전
├── build.gradle.kts        # Backend 전용 빌드 설정
├── settings.gradle.kts
└── gradle.properties
```

Backend 빌드는 반드시 이 디렉터리를 기준으로 실행합니다.

```bash
cd backend
./gradlew build
```

클라이언트와는 소스 코드를 직접 참조하지 않고 HTTP API 계약으로만 통신합니다.
