from pathlib import Path

from PIL import Image, ImageCms

import pytest
from conftest import BACKGROUND, MARKER, ORIENTATION

from imaging import DecodeError, make_thumbnail

FIXTURES = Path(__file__).resolve().parent / "fixtures"
GPS_IFD = 0x8825


def open_output(path):
    image = Image.open(path)
    assert image.format == "WEBP"
    return image


def is_marker(pixel):
    red, green, blue = pixel[:3]
    return red > 200 and blue < 60


def corner(image, where):
    width, height = image.size
    x = 2 if "left" in where else width - 3
    y = 2 if "top" in where else height - 3
    return image.convert("RGB").getpixel((x, y))


# EXIF 방향을 픽셀에 반영하면 저장된 왼쪽 위 표시가 보이는 화면의 이 모서리로 간다.
@pytest.mark.parametrize("orientation, marker_corner, portrait", [
    (1, "top-left", False),
    (2, "top-right", False),
    (3, "bottom-right", False),
    (4, "bottom-left", False),
    (5, "top-left", True),
    (6, "top-right", True),
    (7, "bottom-right", True),
    (8, "bottom-left", True),
])
def test_JPEG의_EXIF_방향을_픽셀에_굽는다(make_jpeg, tmp_path, orientation, marker_corner, portrait):
    out = tmp_path / "out.webp"

    make_thumbnail(make_jpeg(orientation), out, max_edge=1080)

    image = open_output(out)
    assert (image.height > image.width) == portrait
    assert is_marker(corner(image, marker_corner))


def test_HEIC의_방향은_한_번만_적용된다(make_heic, tmp_path):
    out = tmp_path / "out.webp"

    make_thumbnail(make_heic(6), out, max_edge=1080)

    image = open_output(out)
    assert image.height > image.width
    assert is_marker(corner(image, "top-right"))


@pytest.mark.parametrize("source", ["jpeg", "heic"])
def test_출력에는_EXIF_방향이_없다(make_jpeg, make_heic, tmp_path, source):
    out = tmp_path / "out.webp"

    make_thumbnail(make_jpeg(6) if source == "jpeg" else make_heic(6), out, max_edge=1080)

    assert ORIENTATION not in open_output(out).getexif()


def test_위치_정보를_포함한_EXIF를_쓰지_않는다(tmp_path):
    src = tmp_path / "gps.jpg"
    exif = Image.Exif()
    exif[ORIENTATION] = 6
    exif[0x010F] = "Apple"
    exif.get_ifd(GPS_IFD)[1] = "N"
    Image.new("RGB", (40, 20), BACKGROUND).save(src, "JPEG", exif=exif.tobytes())
    out = tmp_path / "out.webp"

    make_thumbnail(src, out, max_edge=1080)

    assert len(open_output(out).getexif()) == 0


def test_긴_변을_max_edge로_줄인다(make_jpeg, tmp_path):
    out = tmp_path / "out.webp"

    make_thumbnail(make_jpeg(1, size=(4032, 3024)), out, max_edge=1080)

    assert open_output(out).size == (1080, 810)


def test_원본보다_크게_늘리지_않는다(make_jpeg, tmp_path):
    out = tmp_path / "out.webp"

    make_thumbnail(make_jpeg(1, size=(400, 200)), out, max_edge=1080)

    assert open_output(out).size == (400, 200)


def test_1px_이미지도_만든다(tmp_path):
    src = tmp_path / "one.png"
    Image.new("RGB", (1, 1), BACKGROUND).save(src)
    out = tmp_path / "out.webp"

    make_thumbnail(src, out, max_edge=1080)

    assert open_output(out).size == (1, 1)


def test_투명_PNG는_투명도를_유지한다(tmp_path):
    src = tmp_path / "alpha.png"
    image = Image.new("RGBA", (40, 20), (0, 0, 0, 0))
    image.paste((*MARKER, 255), (0, 0, 10, 5))
    image.save(src)
    out = tmp_path / "out.webp"

    make_thumbnail(src, out, max_edge=1080)

    result = open_output(out).convert("RGBA")
    assert result.getpixel((30, 15))[3] == 0
    assert result.getpixel((2, 2))[3] == 255


def test_팔레트_PNG도_만든다(tmp_path):
    src = tmp_path / "palette.png"
    Image.new("RGB", (40, 20), BACKGROUND).convert("P").save(src)
    out = tmp_path / "out.webp"

    make_thumbnail(src, out, max_edge=1080)

    assert open_output(out).size == (40, 20)


def test_색_프로필은_유지한다(tmp_path):
    src = tmp_path / "p3.jpg"
    profile = ImageCms.ImageCmsProfile(ImageCms.createProfile("sRGB")).tobytes()
    Image.new("RGB", (40, 20), BACKGROUND).save(src, "JPEG", icc_profile=profile)
    out = tmp_path / "out.webp"

    make_thumbnail(src, out, max_edge=1080)

    assert open_output(out).info.get("icc_profile") == profile


def test_모드를_바꾸면_원래_색_프로필은_붙이지_않는다(tmp_path):
    src = tmp_path / "gray.jpg"
    profile = ImageCms.ImageCmsProfile(ImageCms.createProfile("sRGB")).tobytes()
    Image.new("L", (40, 20), 128).save(src, "JPEG", icc_profile=profile)
    out = tmp_path / "out.webp"

    make_thumbnail(src, out, max_edge=1080)

    assert open_output(out).info.get("icc_profile") is None


# 다시 해도 같은 결과인 손상은 모두 DecodeError여야 워커가 끝없이 다시 하지 않는다. 퍼징으로 찾은 파일이다.
@pytest.mark.parametrize("name", [
    "truncated-decoder.heic", "broken-exif.heic", "heif-security-limit.heic", "broken-exif-rewrite.jpg",
])
def test_디코더나_EXIF가_깨진_파일은_DecodeError다(tmp_path, name):
    with pytest.raises(DecodeError):
        make_thumbnail(FIXTURES / name, tmp_path / "out.webp", max_edge=1080)


# 파일은 작아도 펼치면 메모리를 크게 쓴다. 렌더러(2GB)를 지키려고 펼칠 크기가 한도를 넘으면 만들지 않는다.
def test_펼칠_화소_수가_한도를_넘으면_DecodeError다(tmp_path):
    src = tmp_path / "huge.png"
    Image.new("L", (9000, 8000), 0).save(src)

    with pytest.raises(DecodeError):
        make_thumbnail(src, tmp_path / "out.webp", max_edge=1080)


def test_iPhone_파노라마_크기는_만든다(tmp_path):
    src = tmp_path / "pano.png"
    Image.new("L", (16000, 4000), 0).save(src)
    out = tmp_path / "out.webp"

    make_thumbnail(src, out, max_edge=1080)

    assert open_output(out).size == (1080, 270)


# JPEG는 줄여 읽으므로 원본이 한도보다 커도 펼치는 크기는 작다. 가장 무거운 사진이라 썸네일이 가장 필요하다.
def test_한도보다_큰_JPEG도_줄여_읽어_만든다(tmp_path):
    src = tmp_path / "108mp.jpg"
    Image.new("L", (12000, 9000), 0).save(src, "JPEG")
    out = tmp_path / "out.webp"

    make_thumbnail(src, out, max_edge=1080)

    assert open_output(out).size == (1080, 810)


def test_메모리가_부족하면_DecodeError가_아니라_그대로_던진다(tmp_path, monkeypatch):
    def out_of_memory(*args, **kwargs):
        raise MemoryError

    monkeypatch.setattr(Image, "open", out_of_memory)

    with pytest.raises(MemoryError):
        make_thumbnail(tmp_path / "a.jpg", tmp_path / "out.webp", max_edge=1080)


def test_업로드에서_허용하지_않는_형식은_DecodeError다(tmp_path):
    src = tmp_path / "a.tiff"
    Image.new("RGB", (40, 20), BACKGROUND).save(src, "TIFF")

    with pytest.raises(DecodeError):
        make_thumbnail(src, tmp_path / "out.webp", max_edge=1080)


def test_원본을_읽을_수_없는_환경_오류는_DecodeError가_아니다(tmp_path):
    with pytest.raises(OSError) as raised:
        make_thumbnail(tmp_path, tmp_path / "out.webp", max_edge=1080)

    assert not isinstance(raised.value, DecodeError)


def test_손상된_파일은_DecodeError다(tmp_path):
    src = tmp_path / "broken.jpg"
    src.write_bytes(b"\xff\xd8\xff\xe0 not really a jpeg")

    with pytest.raises(DecodeError):
        make_thumbnail(src, tmp_path / "out.webp", max_edge=1080)

    assert not (tmp_path / "out.webp").exists()
