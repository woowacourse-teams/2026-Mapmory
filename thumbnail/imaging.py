"""사진 한 장을 목록용 썸네일로 줄인다. 키나 저장소는 모르고 파일 경로만 다룬다."""

from pathlib import Path

from PIL import Image, ImageOps, UnidentifiedImageError
from pillow_heif import register_heif_opener

# HEIC는 반드시 Pillow 플러그인으로 연다. 플러그인은 irot/imir를 적용하고 EXIF 방향을 1로 되돌린다.
# pillow_heif.open_heif()는 되돌리지 않아 아래 exif_transpose와 겹치면 두 번 돈다.
register_heif_opener()

WEBP_QUALITY = 75


class DecodeError(Exception):
    """원본을 이미지로 읽을 수 없다. 다시 해도 결과가 같다."""


def make_thumbnail(src, dst, max_edge=1080):
    """방향을 픽셀에 굽고 긴 변을 max_edge 이하로 줄여 WebP로 저장한다. 원본보다 키우지 않는다.

    EXIF는 쓰지 않는다. 방향 태그가 남으면 앱이 한 번 더 돌리고, 위치 정보도 함께 빠진다.
    """
    if max_edge < 1:
        raise ValueError("max_edge must be positive")
    image = _decode(src, max_edge)
    icc_profile = image.info.get("icc_profile")
    image = _to_saveable_mode(ImageOps.exif_transpose(image))
    image.thumbnail((max_edge, max_edge), Image.Resampling.LANCZOS)
    _save_atomically(image, Path(dst), icc_profile)


def _decode(src, max_edge):
    try:
        image = Image.open(src)
        # JPEG는 필요한 크기 근처로 줄여 읽어 메모리와 시간을 아낀다. 다른 형식에서는 아무 일도 하지 않는다.
        image.draft("RGB", (max_edge, max_edge))
        image.load()
        return image
    except FileNotFoundError:
        raise
    except (UnidentifiedImageError, Image.DecompressionBombError, OSError, SyntaxError, ValueError) as e:
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
