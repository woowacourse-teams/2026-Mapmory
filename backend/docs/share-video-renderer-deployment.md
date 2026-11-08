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

## 썸네일 워커 (#631, ADR 0029)

같은 렌더러 서버에서 사진 썸네일 워커도 돈다. 기본값은 꺼져 있고, 켜기 전까지 렌더러 동작은 바뀌지 않는다.

### 배포에 들어가는 것

- `buildspec-renderer.yml`이 `thumbnail/`의 스크립트와 `requirements.txt`를 `/opt/mapmory-renderer/thumbnail`로 보낸다.
- `install.sh`가 그 아래에 가상환경(`.venv`)을 만들고 고정한 버전(Pillow, pillow-heif)을 설치한다. `python3`와 `python3-venv`가 없으면 설치를 멈춘다.
  - Ubuntu 22.04: `sudo apt-get install -y python3-venv`
  - pillow-heif wheel 안에 libheif가 들어 있어 따로 설치할 것은 없다.
- 작업 디렉터리 `/var/lib/mapmory-renderer/thumbnails`를 만든다.

### 켜기 전에 확인할 것

1. 렌더러 인스턴스 역할의 S3 권한: `{p}/travel-records/*` GetObject, `{p}/derived/*` PutObject와 삭제(버전 포함), 없는 키 HeadObject가 403이 아니라 404인지.
2. 렌더러의 Python 버전이 3.10 이상인지(`python3 --version`).
3. 실제 사진 한 장 변환: `/opt/mapmory-renderer/thumbnail/.venv/bin/python make_thumbnail.py in.heic out.webp`와 `/usr/bin/time -v`로 시간과 최대 메모리.
4. 기준값: 켜기 전에 #631 5-1절의 S3 원본 크기 통계를 남긴다. 켜고 나면 다시 잴 수 없다.

### 켜기

`/etc/mapmory-renderer.env`에 다음을 넣고 서비스를 다시 띄운다.

```bash
THUMBNAIL_WORKER_ENABLED=true
THUMBNAIL_PYTHON_COMMAND=/opt/mapmory-renderer/thumbnail/.venv/bin/python
THUMBNAIL_SCRIPT_DIRECTORY=/opt/mapmory-renderer/thumbnail
THUMBNAIL_WORK_DIRECTORY=/var/lib/mapmory-renderer/thumbnails
```

API 서버에서는 켜지 않는다(기본값 `false`).

### 켠 뒤 볼 것

- `mapmory.thumbnail.worker{result}`: `created`가 늘고 `failed`·`given_up`이 드문지. `unsupported_key`는 접두사를 붙이기 전의 옛 키 수다.
- `mapmory.thumbnail.backlog`: 한 시간이 넘었는데 썸네일이 없는 사진 수. 백필이 끝나면 0에 가까워야 한다. 남으면 `given_up`·`undecodable` 로그로 원인을 본다.
- `SELECT COUNT(*) FROM record_media WHERE thumb_key IS NULL OR thumb_key = '';`
- 렌더러 CPU 크레딧(CloudWatch). 백필 중 바닥나면 `THUMBNAIL_WORKER_BATCH_SIZE`를 줄이거나 `THUMBNAIL_WORKER_POLL_DELAY`를 늘린다.
- 영상 처리 시간(`renderDurationMs`)이 켜기 전과 같은지. 워커는 영상을 만드는 동안 쉰다.

### 끄기

`THUMBNAIL_WORKER_ENABLED=false`로 바꾸고 다시 띄운다. 이미 채운 `thumb_key`는 그대로 쓰이고, 남은 사진은 원본으로 보인다.
