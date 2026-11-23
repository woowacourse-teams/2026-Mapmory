import sys
from pathlib import Path

import pytest
from PIL import Image
from pillow_heif import register_heif_opener

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))
register_heif_opener()

ORIENTATION = 0x0112
MARKER = (255, 0, 0)
BACKGROUND = (0, 0, 255)


def stored_landscape(width=400, height=200):
    """저장된 픽셀은 가로이고, 왼쪽 위 모서리에 빨간 표시가 있다."""
    image = Image.new("RGB", (width, height), BACKGROUND)
    image.paste(MARKER, (0, 0, width // 4, height // 4))
    return image


def save_with_orientation(image, path, fmt, orientation):
    exif = Image.Exif()
    exif[ORIENTATION] = orientation
    image.save(path, fmt, exif=exif.tobytes(), quality=95)
    return path


@pytest.fixture
def make_jpeg(tmp_path):
    def make(orientation, size=(400, 200)):
        return save_with_orientation(stored_landscape(*size), tmp_path / f"o{orientation}.jpg", "JPEG", orientation)
    return make


@pytest.fixture
def make_heic(tmp_path):
    def make(orientation):
        return save_with_orientation(stored_landscape(), tmp_path / f"o{orientation}.heic", "HEIF", orientation)
    return make
