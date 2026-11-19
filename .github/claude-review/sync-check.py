"""도메인 동기화 수정안을 기계적으로 거른다. Claude가 없는 job에서, 새로 받은 체크아웃 위에서 돈다.

사용: python3 sync-check.py <원본 디렉터리> <수정안 디렉터리> <ADR 변경 true|false> "<근거 번호들>"
- 원본: 지금 backend-develop의 domain.md, policies.md
- 수정안: Claude가 고친 domain.md, policies.md, summary.md

하는 일:
1. ADR 변경이 없으면 domain.md 수정을 버리고, policies.md에서 정책 문구(표의 첫 칸)가 바뀌거나 새로 생긴 행도 버린다.
   상태·비고 칸만 바뀐 행은 남긴다.
2. 남은 수정에서 근거(이번에 바뀐 ADR, 이번에 머지된 PR) 번호가 없는 새 줄과, 지운 줄을 요약에 붙인다.
3. 결과 파일을 수정안 디렉터리에 다시 쓰고, 바뀐 파일 이름을 표준 출력 마지막 줄에 공백으로 구분해 낸다.
"""

import difflib
import re
import sys
from pathlib import Path

orig_dir, prop_dir, adr_changed, sources = Path(sys.argv[1]), Path(sys.argv[2]), sys.argv[3] == "true", sys.argv[4].split()
sources = [s.replace("_", " ") for s in sources]
source_re = re.compile(r"(^|[^0-9])(" + "|".join(map(re.escape, sources)) + r")([^0-9]|$)") if sources else None

notes: list[str] = []


def first_cell(line: str) -> str | None:
    if not line.startswith("|"):
        return None
    cells = [c.strip() for c in line.strip().strip("|").split("|")]
    return cells[0] if cells else None


def filter_policies(orig: list[str], new: list[str]) -> list[str]:
    """ADR 변경이 없을 때: 같은 정책 행의 상태·비고 변경만 남긴다.
    정책 문구(표의 첫 칸)가 바뀐 행, 새로 생기거나 지운 행, 표 밖 문장 변경은 원본으로 되돌린다."""
    sm = difflib.SequenceMatcher(a=orig, b=new, autojunk=False)
    out: list[str] = []
    dropped: list[str] = []
    for tag, i1, i2, j1, j2 in sm.get_opcodes():
        if tag == "equal":
            out += orig[i1:i2]
        elif tag == "replace" and i2 - i1 == j2 - j1:
            for o, n in zip(orig[i1:i2], new[j1:j2]):
                key = first_cell(o)
                if key and first_cell(n) == key:
                    out.append(n)  # 같은 정책 행에서 상태·비고만 바뀌었다
                else:
                    out.append(o)
                    dropped.append(n.strip())
        else:
            out += orig[i1:i2]
            dropped += [l.strip() for l in (new[j1:j2] or orig[i1:i2])]
    for line in filter(None, dropped):
        notes.append(f"- `policies.md`: ADR 변경 없이 정책 문구·행·문장을 바꾸려 해 버렸다: {line[:200]}")
    return out


changed: list[str] = []
for name in ("domain.md", "policies.md"):
    orig = (orig_dir / name).read_text().splitlines(keepends=True)
    prop_path = prop_dir / name
    new = prop_path.read_text().splitlines(keepends=True)
    if not adr_changed:
        if name == "domain.md":
            if new != orig:
                notes.append("- `domain.md`: ADR 변경이 없어 수정을 버렸다. 규칙을 바꿔야 한다면 ADR로 남긴다.")
            new = orig
        else:
            new = filter_policies(orig, new)
    prop_path.write_text("".join(new))
    if new == orig:
        continue
    changed.append(name)
    sm = difflib.SequenceMatcher(a=orig, b=new, autojunk=False)
    for tag, i1, i2, j1, j2 in sm.get_opcodes():
        if tag == "equal":
            continue
        for line in orig[i1:i2] if tag == "delete" else []:
            if line.strip():
                notes.append(f"- `{name}` 지운 줄: {line.strip()[:200]}")
        if tag == "replace" and i2 - i1 > j2 - j1:
            notes.append(f"- `{name}` 줄이 {i2 - i1 - (j2 - j1)}개 줄었다(줄 {i1 + 1}~{i2}). 지운 결정이 없는지 본다.")
        for line in new[j1:j2]:
            text = line.strip()
            if text and not (source_re and source_re.search(text)):
                notes.append(f"- `{name}` 근거 없는 줄: {text[:200]}")

if notes:
    with open(prop_dir / "summary.md", "a") as f:
        f.write("\n## 기계 검사\n\n이번에 바뀐 ADR이나 이번에 머지된 PR 번호가 없는 새 줄, 지운 줄, 버린 수정이다. 사람이 먼저 볼 곳이다.\n\n")
        f.write("\n".join(notes) + "\n")

print(" ".join(changed))
