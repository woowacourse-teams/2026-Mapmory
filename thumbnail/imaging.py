"""사진 한 장을 목록용 썸네일로 줄인다. 키나 저장소는 모르고 파일 경로만 다룬다."""

import warnings
from pathlib import Path

from PIL import Image, ImageOps
from pillow_heif import register_heif_opener

# HEIC는 반드시 Pillow 플러그인으로 연다. 플러그인은 irot/imir를 적용하고 EXIF 방향을 1로 되돌린다.
# pillow_heif.open_heif()는 되돌리지 않아 아래 exif_transpose와 겹치면 두 번 돈다.
register_heif_opener()

WEBP_QUALITY = 75
RGB_MODES = ("RGB", "RGBA", "P", "PA")
# 업로드가 허용하는 형식만 연다(UploadFileType). 나머지 디코더(TIFF, EPS 등)에는 닿지 않게 한다.
ALLOWED_FORMATS = ["JPEG", "PNG", "WEBP", "HEIF"]
# iPhone 48MP(약 4,880만)는 통과시키고 그보다 큰 이미지는 만들지 않는다. RGBA 6,000만 화소를 펼치면 약 240MB다.
Image.MAX_IMAGE_PIXELS = 60_000_000


class DecodeError(Exception):
    """원본을 이미지로 읽을 수 없다. 다시 해도 결과가 같다."""


def make_thumbnail(src, dst, max_edge=1080):
    """방향을 픽셀에 굽고 긴 변을 max_edge 이하로 줄여 WebP로 저장한다. 원본보다 키우지 않는다.

    EXIF는 쓰지 않는다. 방향 태그가 남으면 앱이 한 번 더 돌리고, 위치 정보도 함께 빠진다.
    """
    if max_edge < 1:
        raise ValueError("max_edge must be positive")
    image = _decode_upright(src, max_edge)
    # 색 프로필은 RGB 계열 원본일 때만 붙인다. 회색조·CMYK 프로필을 RGB 출력에 붙이면 색이 틀어진다.
    icc_profile = image.info.get("icc_profile") if image.mode in RGB_MODES else None
    image = _to_saveable_mode(image)
    image.thumbnail((max_edge, max_edge), Image.Resampling.LANCZOS)
    _save_atomically(image, Path(dst), icc_profile)


def _decode_upright(src, max_edge):
    """원본을 읽고 방향을 픽셀에 반영한다. 파일 내용 때문에 실패하면 DecodeError, 실행 환경 때문이면 그 예외 그대로다."""
    try:
        with warnings.catch_warnings():
            # 한도를 넘는 화소 수는 Pillow가 경고만 하고 그대로 펼친다. 렌더러 메모리를 지키려고 오류로 끊는다.
            warnings.simplefilter("error", Image.DecompressionBombWarning)
            image = Image.open(src, formats=ALLOWED_FORMATS)
            # JPEG는 필요한 크기 근처로 줄여 읽어 메모리와 시간을 아낀다. 다른 형식에서는 아무 일도 하지 않는다.
            image.draft("RGB", (max_edge, max_edge))
            image.load()
        # 복사본을 만들지 않아 큰 사진에서 메모리를 아낀다. 깨진 EXIF는 여기서 실패할 수 있다.
        ImageOps.exif_transpose(image, in_place=True)
        return image
    except MemoryError:
        raise
    except OSError as e:
        # errno가 있으면 운영체제가 낸 오류(파일 없음, 권한, 디렉터리, 디스크)다. 다시 하면 나을 수 있다.
        # Pillow의 디코딩 오류(잘린 파일, 알 수 없는 형식)는 errno가 없다.
        if e.errno is not None:
            raise
        raise DecodeError(f"{type(e).__name__}: {e}") from e
    except Exception as e:  # noqa: BLE001 - 이 구간은 파일 내용만 다루므로 나머지는 모두 내용 탓이다
        # 디코더마다 던지는 예외가 다르다(libheif 보안 한도는 RuntimeError, 깨진 EXIF 재기록은 TypeError 등).
        raise DecodeError(f"{type(e).__name__}: {e}") from e


def _to_saveable_mode(image):
    if image.mode in ("RGB", "RGBA"):
        return image
    has_alpha = image.mode in ("LA", "PA") or "transparency" in image.info
    return image.convert("RGBA" if has_alpha else "RGB")


def _save_atomically(image, dst, icc_profile):
    # 쓰다 실패해도 반쯤 쓴 파일이 출력 경로에 남지 않게 한다.
    partial = dst.with_name(dst.name + ".partial")
    try:
        image.save(partial, "WEBP", quality=WEBP_QUALITY, method=4, icc_profile=icc_profile)
        partial.replace(dst)
    finally:
        partial.unlink(missing_ok=True)
