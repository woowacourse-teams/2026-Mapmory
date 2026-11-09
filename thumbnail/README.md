# thumbnail

사진 원본 한 장을 목록용 썸네일(WebP)로 줄이는 스크립트다. 렌더러 서버의 썸네일 워커(#631)가 외부 프로세스로 실행한다.
S3 키나 DB는 모르고 입력·출력 파일 경로만 받는다. 키 규칙은 백엔드의 `DerivedObjectKey`가 맡는다.

## 실행

```bash
python3 -m venv .venv
.venv/bin/pip install -r requirements.txt
.venv/bin/python make_thumbnail.py input.heic output.webp --max-edge 1080
```

| 종료 코드 | 뜻 | 워커의 처리 |
| --- | --- | --- |
| 0 | 성공 | 업로드한다 |
| 2 | 원본 내용 때문에 읽을 수 없다(손상, 허용하지 않는 형식, 깨진 EXIF, 펼칠 화소 수 초과) | 다시 해도 같으므로 원본으로 남긴다 |
| 1 | 그 밖의 오류(인자, 파일 없음·권한·디스크 같은 실행 환경, 쓰기 실패) | 다음 차례에 다시 한다. 워커는 종료 코드와 상관없이 시도 횟수에 상한을 둔다 |

## 하는 일

- **방향은 픽셀에 굽고 EXIF는 쓰지 않는다.** 방향 태그가 남으면 앱이 한 번 더 돌린다. 위치 정보도 함께 빠진다.
- **HEIC는 Pillow 플러그인(`register_heif_opener`)으로 연다.** 플러그인은 `irot`/`imir`를 적용하고 EXIF 방향을 1로 되돌린다. `pillow_heif.open_heif()`로 열면 되돌리지 않아 `exif_transpose`와 겹쳐 두 번 돈다(테스트 `test_HEIC의_방향은_한_번만_적용된다`).
- 긴 변을 `--max-edge` 이하로 줄이고, 원본보다 키우지 않는다. WebP quality 75로 저장한다.
- 색 프로필(ICC)은 RGB 계열 원본일 때 유지한다. iPhone 사진은 Display P3라 빼면 색이 바랜다. 회색조·CMYK 원본의 프로필은 RGB 출력과 맞지 않아 뺀다.
- 투명한 PNG는 투명도를 유지한다. 8비트 RGB PNG의 색 키(tRNS) 투명도도 유지한다. 16비트 PNG의 색 키 투명도는 유지하지 못한다(사진 업로드에서는 드물다).
- 16비트 회색조 PNG는 8비트로 줄여 밝기를 유지한다.
- JPEG는 필요한 크기 근처로 줄여 읽어 메모리를 아낀다(`Image.draft`).
- 펼칠 화소 수가 7,000만을 넘으면 만들지 않는다(종료 코드 2). 작은 PNG도 펼치면 GB 단위가 될 수 있어 렌더러(2GB) 메모리를 지키기 위해서다. iPhone 48MP와 파노라마는 통과한다. JPEG는 줄여 읽은 뒤의 크기로 보므로 108MP·200MP 원본도 만든다. PNG는 열기 전에 첫 IHDR의 크기도 한 번 더 본다(Pillow 12.3.0은 APNG를 여는 순간 헤더 크기만큼 메모리를 잡았다. 13에서는 재현되지 않는다). HEIC는 헤더 크기를 속이면 이 검사를 지나 libheif 자체 한도까지 갈 수 있다.
- 업로드가 허용하는 JPEG, PNG, WebP, HEIC만 연다. 그 밖의 형식은 종료 코드 2다.
- 출력은 임시 파일에 쓴 뒤 옮겨, 실패해도 반쯤 쓴 파일이 남지 않는다.

## 버전

렌더러 서버는 Amazon Linux 2023(arm64)이다. 기본 `python3`는 3.9라 `dnf install -y python3.12`로 따로 설치하고, 그 Python으로 가상환경을 만든다(Amazon Linux는 venv가 Python 패키지에 들어 있다).

Pillow는 13.0.0 이상을 쓴다. 13.0.0이 사용자가 올린 파일을 디코딩할 때 걸리는 Critical 보안 문제(JPEG 디코더 버퍼 오버플로, 모드 전환 뒤 낡은 메모리 재사용 등)를 고쳤고, 12.x에는 이 수정이 없다. Pillow 13은 Python 3.11 이상이 필요하다. 두 패키지 모두 이 환경용 wheel이 있고, pillow-heif wheel 안에 libheif가 들어 있어 따로 설치할 것은 없다. CI도 같은 OS·CPU·Python(`amazonlinux:2023` arm64 컨테이너, Python 3.12)에서 돈다.

## 테스트

```bash
.venv/bin/pip install -r requirements-dev.txt
.venv/bin/python -m pytest -q
```

픽스처는 테스트가 만든다. 왼쪽 위에 빨간 표시를 둔 가로 이미지에 EXIF 방향을 넣고, 결과에서 표시가 있어야 할 모서리를 확인한다. HEIC 픽스처는 pillow-heif로 만들어 HEIF 헤더의 회전과 EXIF 방향 6을 함께 가진다. 실제 iPhone 사진과 같은 구조인지는 실기기 사진으로 따로 확인해야 한다.
