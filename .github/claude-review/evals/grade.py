"""회귀 검증 케이스 하나의 리뷰 결과를 채점한다.

사용법: python3 grade.py <cases.json> <case id> <claude 출력 json> <결과 json>

기대한 지적은 같은 파일의 지정한 줄 범위 안에 Nit보다 높은 심각도로 있어야 찾은 것으로 본다.
기대가 비어 있는 케이스는 Nit보다 높은 지적이 하나라도 있으면 실패다(오탐).
기대 밖의 지적은 실패로 치지 않고 결과에 따로 남긴다. 맞는 지적일 수도 있어서 사람이 본다.
"""
import json
import re
import sys

BLOCKING = {"critical", "major", "minor"}


def load_findings(path):
    with open(path, encoding="utf-8") as f:
        raw = json.load(f)
    data = raw.get("structured_output") if isinstance(raw, dict) else None
    if data is None:
        text = raw.get("result", "") if isinstance(raw, dict) else ""
        match = re.search(r"\{.*\}", text, re.S)
        data = json.loads(match.group(0)) if match else {"findings": []}
    return data.get("findings", [])


def matches(finding, expected):
    start, end = expected["lines"]
    line = finding.get("line") or 0
    file = re.sub(r"^(\./|a/|b/)", "", str(finding.get("file", "")))
    return (
        file
        and (expected["file"].endswith(file) or file.endswith(expected["file"]))
        and start <= line <= end
        and str(finding.get("severity", "")).lower() in BLOCKING
    )


def grade(case, findings):
    found, missed = [], []
    used = set()
    for expected in case["expect"]:
        hit = next((i for i, f in enumerate(findings) if matches(f, expected)), None)
        if hit is None:
            missed.append(expected["name"])
        else:
            used.add(hit)
            found.append(expected["name"])
    extra = [f for i, f in enumerate(findings) if i not in used]
    blocking_extra = [f for f in extra if str(f.get("severity", "")).lower() in BLOCKING]
    passed = not missed and (case["expect"] or not blocking_extra)
    return {
        "id": case["id"],
        "passed": bool(passed),
        "found": found,
        "missed": missed,
        "extra": [
            f"[{f.get('severity')}] {f.get('file')}:{f.get('line')} {f.get('title', '')}" for f in extra
        ],
    }


def main():
    cases_path, case_id, output_path, result_path = sys.argv[1:5]
    with open(cases_path, encoding="utf-8") as f:
        case = next(c for c in json.load(f)["cases"] if c["id"] == case_id)
    try:
        findings = load_findings(output_path)
    except (OSError, ValueError) as error:
        findings = []
        print(f"리뷰 결과를 읽지 못했다: {error}", file=sys.stderr)
    result = grade(case, findings)
    with open(result_path, "w", encoding="utf-8") as f:
        json.dump(result, f, ensure_ascii=False, indent=2)
    print(json.dumps(result, ensure_ascii=False, indent=2))
    sys.exit(0 if result["passed"] else 1)


if __name__ == "__main__":
    main()
