"""도메인 동기화 수정안을 기계적으로 거른다. Claude가 없는 job에서, 새로 받은 체크아웃 위에서 돈다.

사용: python3 sync-check.py <원본 디렉터리> <수정안 디렉터리> <ADR 변경 true|false> "<근거 번호들>"
- 원본: 지금 backend-develop의 domain.md, policies.md
- 수정안: Claude가 고친 domain.md, policies.md, summary.md

하는 일:
1. ADR 변경이 없으면 domain.md 수정을 버리고, policies.md는 표 행의 "상태"·"비고" 칸 변경만 남긴다.
   정책 문구·보드·리뷰 판정 칸, 행 추가·삭제, 표 밖 문장 변경은 버린다.
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


def cells(line: str) -> list[str] | None:
    if not line.startswith("|"):
        return None
    return [c.strip() for c in line.strip().strip("|").split("|")]


def headers(lines: list[str]) -> list[list[str] | None]:
    """각 줄이 속한 표의 머리글. 표 밖이면 None."""
    out: list[list[str] | None] = []
    current = None
    for i, line in enumerate(lines):
        if not line.startswith("|"):
            current = None
        elif current is None and i + 1 < len(lines) and re.match(r"^\|[\s:|-]+\|\s*$", lines[i + 1]):
            current = cells(line)
        out.append(current)
    return out


def editable_columns(header: list[str] | None) -> set[int]:
    """ADR 없이 코드 변경으로 바꿔도 되는 칸: 첫 칸이 아닌 "상태" 칸과 "비고" 칸. 리뷰 판정·리뷰 때·보드 칸은 안 된다."""
    if not header:
        return set()
    return {i for i, h in enumerate(header) if (h == "상태" and i > 0) or "비고" in h}


# 코드 변경으로 갈 수 있는 상태. "결정 필요"·"보류"·"보드와 다름"·"앱"에서 나오거나 그리로 가는 것은 결정이 필요하다.
CODE_STATES = {"없음", "일부", "있음"}


def state_change_ok(header: list[str] | None, old: list[str], new: list[str]) -> bool:
    """결정이 필요한 상태의 행은 상태·비고 모두 ADR 없이 바꾸지 않는다. 비고에 결정 내용이나 리뷰 지시가 들어 있다."""
    if not header or "상태" not in header:
        return True
    i = header.index("상태")
    return old[i] in CODE_STATES and new[i] in CODE_STATES


def filter_policies(orig: list[str], new: list[str]) -> list[str]:
    """ADR 변경이 없을 때: 같은 표 행에서 상태·비고 칸 변경만 남긴다.
    다른 칸(정책 문구, 보드, 리뷰 판정 등)이 바뀐 행, 새로 생기거나 지운 행, 표 밖 문장 변경은 원본으로 되돌린다."""
    heads = headers(orig)
    sm = difflib.SequenceMatcher(a=orig, b=new, autojunk=False)
    out: list[str] = []
    dropped: list[str] = []
    kept: list[str] = []
    for tag, i1, i2, j1, j2 in sm.get_opcodes():
        if tag == "equal":
            out += orig[i1:i2]
        elif tag == "replace" and i2 - i1 == j2 - j1:
            for k, (o, n) in enumerate(zip(orig[i1:i2], new[j1:j2])):
                oc, nc = cells(o), cells(n)
                allowed = editable_columns(heads[i1 + k])
                same_shape = oc is not None and nc is not None and len(oc) == len(nc)
                if same_shape and allowed and all(a == b for i, (a, b) in enumerate(zip(oc, nc)) if i not in allowed) \
                        and state_change_ok(heads[i1 + k], oc, nc):
                    out.append(n)  # 같은 행에서 상태(없음·일부·있음 사이)·비고만 바뀌었다
                    kept.append(n.strip())
                else:
                    out.append(o)
                    dropped.append(n.strip())
        else:
            out += orig[i1:i2]
            dropped += [l.strip() for l in (new[j1:j2] or orig[i1:i2])]
    for line in filter(None, dropped):
        notes.append(f"- `policies.md`: ADR 변경 없이 상태·비고 밖을 바꾸거나 결정이 필요한 상태를 바꾸려 해 버렸다: {line[:200]}")
    for line in kept:
        notes.append(f"- `policies.md` 코드 변경으로 바꾼 행(비고에 결정처럼 적힌 말이 없는지 본다): {line[:200]}")
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
