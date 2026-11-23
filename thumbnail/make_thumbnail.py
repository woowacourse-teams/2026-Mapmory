"""python3 make_thumbnail.py <원본> <출력.webp> [--max-edge N]
   python3 make_thumbnail.py --check   # 변환 없이 Pillow·pillow-heif를 불러올 수 있는지만 본다

종료 코드: 0 성공, 2 원본 내용 때문에 읽을 수 없음(다시 해도 같음),
1 그 밖의 오류(인자, 파일 없음·권한 같은 실행 환경, 쓰기 실패).
"""

import argparse
import sys

from imaging import DecodeError, make_thumbnail

EXIT_FAILED = 1
EXIT_UNDECODABLE = 2


class _Parser(argparse.ArgumentParser):
    # argparse는 인자 오류에 2로 끝나는데, 2는 '디코딩 불가'라 겹치지 않게 1로 바꾼다.
    def error(self, message):
        self.print_usage(sys.stderr)
        print(f"error: {message}", file=sys.stderr)
        sys.exit(EXIT_FAILED)


def _positive(value):
    number = int(value)
    if number < 1:
        raise argparse.ArgumentTypeError("must be positive")
    return number


def main(argv=None):
    argv = sys.argv[1:] if argv is None else argv
    if argv == ["--check"]:
        # imaging을 불러오면서 Pillow와 pillow-heif도 이미 불러왔다. 여기까지 왔으면 준비된 것이다.
        return 0
    parser = _Parser()
    parser.add_argument("src")
    parser.add_argument("dst")
    parser.add_argument("--max-edge", type=_positive, default=1080)
    args = parser.parse_args(argv)
    try:
        make_thumbnail(args.src, args.dst, max_edge=args.max_edge)
    except DecodeError as e:
        print(f"undecodable: {e}", file=sys.stderr)
        return EXIT_UNDECODABLE
    except Exception as e:  # noqa: BLE001 - 워커가 종료 코드로만 판단하므로 원인은 stderr에 남긴다
        print(f"failed: {type(e).__name__}: {e}", file=sys.stderr)
        return EXIT_FAILED
    return 0


if __name__ == "__main__":
    sys.exit(main())
