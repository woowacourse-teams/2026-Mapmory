from PIL import Image, ImageCms

import pytest
from conftest import BACKGROUND, MARKER, ORIENTATION

from imaging import DecodeError, make_thumbnail


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
    (3, "bottom-right", False),
    (6, "top-right", True),
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


def test_손상된_파일은_DecodeError다(tmp_path):
    src = tmp_path / "broken.jpg"
    src.write_bytes(b"\xff\xd8\xff\xe0 not really a jpeg")

    with pytest.raises(DecodeError):
        make_thumbnail(src, tmp_path / "out.webp", max_edge=1080)

    assert not (tmp_path / "out.webp").exists()
