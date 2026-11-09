import subprocess
import sys
from pathlib import Path

from PIL import Image

SCRIPT = Path(__file__).resolve().parents[1] / "make_thumbnail.py"


def run(*args):
    return subprocess.run([sys.executable, str(SCRIPT), *map(str, args)], capture_output=True, text=True)


def test_성공하면_0이고_파일을_만든다(make_jpeg, tmp_path):
    out = tmp_path / "out.webp"

    result = run(make_jpeg(6), out, "--max-edge", "100")

    assert result.returncode == 0, result.stderr
    assert max(Image.open(out).size) == 100


def test_디코딩할_수_없으면_2다(tmp_path):
    src = tmp_path / "broken.heic"
    src.write_bytes(b"garbage")

    assert run(src, tmp_path / "out.webp").returncode == 2


def test_입력이_없거나_인자가_틀리면_1이다(tmp_path):
    assert run(tmp_path / "missing.jpg", tmp_path / "out.webp").returncode == 1
    assert run("only-one-arg").returncode == 1
    assert run(tmp_path / "a.jpg", tmp_path / "b.webp", "--max-edge", "0").returncode == 1


def test_원본이_디렉터리처럼_환경_문제면_1이다(tmp_path):
    assert run(tmp_path, tmp_path / "out.webp").returncode == 1


# 워커가 켜질 때 경로·Python·패키지가 준비됐는지 한 번 확인한다. 스크립트를 불러오면 Pillow와 pillow-heif도 불러온다.
def test_check는_변환_없이_0으로_끝난다():
    assert run("--check").returncode == 0
