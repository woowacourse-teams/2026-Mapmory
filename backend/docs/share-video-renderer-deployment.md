# 공유 영상 렌더러 개발환경 배포 분리 기록

## 목표

API 서버의 배포 흐름과 영상 렌더러 서버의 배포 흐름을 개발환경에서 분리한다. 두 서버는 현재 같은 백엔드 저장소와 백엔드 JAR를 사용하지만, 배포 트리거와 CodeDeploy 대상은 별도로 운영한다.

## 현재 구성

| 파이프라인 | 소스 브랜치 | 빌드 | 배포 대상 |
| --- | --- | --- | --- |
| `mapmory-backend-dev-pipeline` | `backend-develop` | 기존 백엔드 빌드 | 기존 API 개발 서버 |
| `mapmory-renderer-pipeline` | `renderer-release` | `mapmory-backend-dev-build`, `backend/buildspec-renderer.yml` | CodeDeploy 앱 `mapmory-backend`, 배포 그룹 `mapmory-renderer-dev` |

렌더러 파이프라인은 GitHub OAuth 앱 소스와 `PollForSourceChanges=false`를 사용한다. 아티팩트 버킷은 `techcourse-project-2026-artifacts`다. 두 파이프라인 모두 개발환경 구성이다.

## 확인한 결과

- 렌더러 파이프라인의 Source → Build → Deploy 실행이 성공했다.
- 기존 API 파이프라인에서 렌더러 전용 Build/Deploy 스테이지를 제거하고 Source → Build → Deploy 구성으로 저장했다.
- 개발환경에서 영상 생성 요청부터 작업 완료, MP4 다운로드까지 확인했다.
- API 서버의 실제 `SHARE_VIDEO_WORKER_ENABLED` 값과 GitHub 푸시가 각 파이프라인만 실행하는지는 IAM 권한 부족으로 확인하지 못했다.
- 운영환경 배포는 아직 구성하거나 실행하지 않았다.

## 다음 확인 항목

1. API 인스턴스의 `SHARE_VIDEO_WORKER_ENABLED=false`와 렌더러 인스턴스의 `true` 설정을 각각 확인한다.
2. `renderer-release`를 `backend-develop`과 동기화하는 절차를 정하고, 장기 분기로 인한 누락을 방지한다.
3. `renderer-release`에 푸시했을 때 렌더러 파이프라인만 실행되고 API 파이프라인은 실행되지 않는지 확인한다. 반대 방향도 확인한다.
4. 위 확인을 마친 뒤 운영환경의 별도 브랜치·파이프라인·CodeDeploy 대상을 구성한다.

## 운영 원칙

- `renderer-release`는 개발환경 렌더러 배포 전용 브랜치다.
- API 배포 브랜치와 장기간 벌어지지 않도록 정기적으로 동기화한다.
- 파이프라인 실행은 브랜치 푸시를 통해서만 유발한다. 이 문서 작성 시점에는 푸시 이벤트 격리를 실제로 검증하지 않았다.
- 렌더러 프로세스는 현재 인스턴스 1대에서 동시 작업 1개로 운영한다. 동시성 확대는 작업별 소요 시간과 메모리 지표를 확인한 뒤 결정한다.
