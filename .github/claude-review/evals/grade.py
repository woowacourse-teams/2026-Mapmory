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
    """리뷰 결과를 읽는다. 리뷰가 돌지 않았거나 결과를 읽을 수 없으면 ValueError를 던진다."""
    with open(path, encoding="utf-8") as f:
        raw = json.load(f)
    if not isinstance(raw, dict):
        raise ValueError("Claude 출력 형식이 아니다")
    if raw.get("is_error"):
        raise ValueError(f"Claude 실행 오류: {str(raw.get('result', ''))[:200]}")
    data = raw.get("structured_output")
    if data is None:
        match = re.search(r"\{.*\}", str(raw.get("result", "")), re.S)
        if not match:
            raise ValueError("findings를 찾지 못했다")
        data = json.loads(match.group(0))
    if not isinstance(data, dict) or not isinstance(data.get("findings"), list):
        raise ValueError("findings 형식이 아니다")
    return data["findings"]


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
        result = grade(case, load_findings(output_path))
    except (OSError, ValueError) as error:
        # 리뷰가 돌지 않은 것을 "지적 없음"으로 보면 오탐 케이스가 통과로 보인다. 따로 실패로 남긴다.
        result = {"id": case_id, "passed": False, "found": [], "missed": [f"리뷰 결과 없음: {error}"], "extra": []}
    with open(result_path, "w", encoding="utf-8") as f:
        json.dump(result, f, ensure_ascii=False, indent=2)
    print(json.dumps(result, ensure_ascii=False, indent=2))
    sys.exit(0 if result["passed"] else 1)


if __name__ == "__main__":
    main()
