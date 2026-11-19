"""기계 검사(Semgrep, PIT) 결과를 Claude 리뷰가 읽을 목록과 PR 체크 주석으로 바꾼다.

사용법:
  python3 signals.py semgrep <semgrep --json 출력> <목록 출력 경로>
  python3 signals.py pit <PIT mutations.xml> <목록 출력 경로>

목록은 마크다운이고, 문제가 없으면 "없음"을 쓴다. 표준 출력으로는 GitHub 주석(::warning)을 낸다.
"""
import json
import sys
import xml.etree.ElementTree as ET

# 살아남은 변이: 코드를 바꿨는데도 테스트가 통과했다(SURVIVED). 바뀐 줄을 실행하는 테스트가 아예 없다(NO_COVERAGE).
WEAK_STATUSES = {"SURVIVED", "NO_COVERAGE"}


def escape(text):
    return str(text).replace("%", "%25").replace("\r", "%0D").replace("\n", "%0A")


def semgrep(report_path, out_path):
    with open(report_path, encoding="utf-8") as f:
        results = json.load(f).get("results", [])
    lines = []
    for r in results:
        rule = r["check_id"].split(".")[-1]
        message = " ".join(r["extra"]["message"].split())
        lines.append(f"- `{r['path']}:{r['start']['line']}` {rule}: {message}")
        print(f"::warning file={r['path']},line={r['start']['line']},title=Semgrep {rule}::{escape(message)}")
    write(out_path, "Semgrep", lines)


def pit(report_path, out_path):
    root = ET.parse(report_path).getroot()
    lines = []
    total = killed = 0
    for m in root.iter("mutation"):
        total += 1
        status = m.get("status")
        if status not in WEAK_STATUSES:
            killed += 1
            continue
        clazz = m.findtext("mutatedClass")
        package_path = "/".join(clazz.split(".")[:-1])
        path = f"backend/src/main/java/{package_path}/{m.findtext('sourceFile')}"
        line = m.findtext("lineNumber")
        method = m.findtext("mutatedMethod")
        description = m.findtext("description")
        lines.append(f"- `{path}:{line}` {method}(): {description} → {status}")
        print(f"::warning file={path},line={line},title=PIT {status}::{escape(f'{method}(): {description}. 이렇게 바꿔도 테스트가 실패하지 않는다.')}")
    score = f"변이 {total}개 중 {killed}개를 테스트가 잡았다({killed * 100 // total if total else 0}%)."
    print(f"::notice title=PIT::{score} 살아남은 변이 {len(lines)}개.")
    write(out_path, "PIT", lines, score)


def write(out_path, name, lines, header=None):
    with open(out_path, "w", encoding="utf-8") as f:
        if header:
            f.write(header + "\n\n")
        f.write("\n".join(lines) + "\n" if lines else f"{name}: 없음\n")


if __name__ == "__main__":
    kind, report, out = sys.argv[1:4]
    {"semgrep": semgrep, "pit": pit}[kind](report, out)
