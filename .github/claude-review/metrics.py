"""Claude 리뷰를 주간 지표로 잰다. Claude를 부르지 않고 GitHub API와 git만 쓴다.

사용: python3 metrics.py <저장소 owner/name> <일 수> <출력 md>
작업 폴더는 이 저장소의 전체 클론이어야 한다(코드 해결률 계산에 PR 커밋이 필요하다).

지표
- 사람 판정: 리뷰 코멘트에 단 👍/👎. 👎 비율이 틀린 지적의 비율이다. AI끼리의 동의가 아닌 유일한 정밀도 신호다.
- 코드 해결률(Atlassian CRR): 코멘트를 단 뒤 같은 PR의 이후 커밋이 그 줄(±2줄)을 바꿨나. 작성자가 AI 에이전트면 "따랐다"는 뜻일 수 있다.
- 소음: 요약 길이, 요약의 "문제없음" 항목, PR당 리뷰 실행 수, 심각도 분포
"""
import json
import re
import subprocess
import sys
from collections import Counter, defaultdict
from datetime import datetime, timedelta, timezone

REPO, DAYS, OUT = sys.argv[1], int(sys.argv[2]), sys.argv[3]
BOT = "github-actions[bot]"
SINCE = datetime.now(timezone.utc) - timedelta(days=DAYS)

# 2026-11-17 ~ 11-20, 리뷰를 줄이기 전(앱 PR 10개, 인라인 지적 33개)
BASELINE = {
    "summary_len": 2574,
    "no_issue_items": 4.4,
    "crr": 0.82,
    "runs_per_pr": "2~9 (배포 PR 26)",
}


def gh(path: str):
    out = subprocess.run(["gh", "api", "--paginate", "--slurp", path], capture_output=True, text=True, check=True).stdout
    pages = json.loads(out)
    items = []
    for page in pages:
        if isinstance(page, list):
            items += page
        elif isinstance(page, dict) and "workflow_runs" in page:
            items += page["workflow_runs"]
        else:
            items.append(page)
    return items


def ts(s: str) -> datetime:
    return datetime.fromisoformat(s.replace("Z", "+00:00"))


def severity(body: str) -> str:
    m = re.search(r"\[(Critical|Major|Minor|Nit)\]", body)
    return m.group(1) if m else "?"


def changed_near(base: str, head: str, path: str, line: int):
    try:
        out = subprocess.run(["git", "diff", "-U0", base, head, "--", path],
                             capture_output=True, text=True, check=True).stdout
    except subprocess.CalledProcessError:
        return None
    for m in re.finditer(r"^@@ -(\d+)(?:,(\d+))? \+", out, re.M):
        start, count = int(m.group(1)), int(m.group(2) or 1)
        if start + max(count, 1) - 1 >= line - 2 and start <= line + 2:
            return True
    return False


prs = [p for p in gh(f"repos/{REPO}/pulls?state=all&sort=updated&direction=desc&per_page=100")
       if ts(p["updated_at"]) >= SINCE and p["user"]["type"] != "Bot"]

comments, summaries, reviewed_prs = [], [], set()
sha_to_pr: dict[str, int] = {}
for p in prs:
    n = p["number"]
    # 배포 PR은 여러 PR의 커밋을 담으니, 커밋→PR 대응은 배포가 아닌 PR을 먼저 쓴다.
    deploy = p["base"]["ref"] not in ("backend-develop", "develop", "frontend-develop")
    for c in gh(f"repos/{REPO}/pulls/{n}/commits?per_page=100"):
        if not deploy or c["sha"] not in sha_to_pr:
            sha_to_pr[c["sha"]] = n
    head = p["head"]["sha"]
    subprocess.run(["git", "fetch", "-q", "origin", f"+refs/pull/{n}/head:refs/remotes/pr/{n}"], check=False)
    for c in gh(f"repos/{REPO}/pulls/{n}/comments?per_page=100"):
        if c["user"]["login"] != BOT or c.get("in_reply_to_id") or ts(c["created_at"]) < SINCE:
            continue
        line = c.get("original_line") or c.get("line") or 0
        r = c.get("reactions", {})
        comments.append({
            "pr": n, "sev": severity(c["body"]), "up": r.get("+1", 0), "down": r.get("-1", 0),
            "resolved": changed_near(c["original_commit_id"], head, c["path"], line) if line else None,
            "url": c["html_url"], "title": c["body"].strip().splitlines()[0][:80],
        })
        reviewed_prs.add(n)
    for c in gh(f"repos/{REPO}/issues/{n}/comments?per_page=100"):
        if c["user"]["login"] == BOT and c["body"].startswith("## Claude 리뷰") and ts(c["updated_at"]) >= SINCE:
            summaries.append({"pr": n, "len": len(c["body"]), "no_issue": c["body"].count("문제없음")})
            reviewed_prs.add(n)

runs = [r for r in gh(f"repos/{REPO}/actions/workflows/claude-code-review.yml/runs?per_page=100&created=>={SINCE:%Y-%m-%d}")
        if r["conclusion"] == "success"]
runs_by_pr = Counter(sha_to_pr.get(r["head_sha"], "?") for r in runs)

evals = gh(f"repos/{REPO}/actions/workflows/claude-review-eval.yml/runs?per_page=20&created=>={SINCE:%Y-%m-%d}")
eval_line = (f"{evals[0]['conclusion']} ({evals[0]['created_at'][:10]}, {evals[0]['html_url']})"
             if evals else "이번 주 실행 없음")

k = len(comments)
judged = [c for c in comments if c["up"] or c["down"]]
down = sum(1 for c in comments if c["down"] and not c["up"])
res = [c for c in comments if c["resolved"] is not None]
crr = sum(bool(c["resolved"]) for c in res) / len(res) if res else None
avg_len = sum(s["len"] for s in summaries) / len(summaries) if summaries else None
avg_no = sum(s["no_issue"] for s in summaries) / len(summaries) if summaries else None
sev = Counter(c["sev"] for c in comments)


def pct(x):
    return "-" if x is None else f"{x:.0%}"


def num(x, fmt="{:.0f}"):
    return "-" if x is None else fmt.format(x)


lines = [
    f"최근 {DAYS}일({SINCE:%Y-%m-%d} 이후) Claude 리뷰 지표다. Claude 없이 GitHub API와 git으로 셌다.",
    "",
    f"리뷰한 PR {len(reviewed_prs)}개, 인라인 지적 {k}개(Critical {sev['Critical']} · Major {sev['Major']} · Minor {sev['Minor']} · Nit {sev['Nit']}).",
    "",
    "| 지표 | 기준선(11/17~20) | 이번 주 | 목표 |",
    "|---|---|---|---|",
    f"| 👎 비율 (사람이 틀렸다고 표시) | 잴 수 없음 | {pct(down / len(judged)) if judged else '-'} ({down}/{len(judged)}) | 5% 미만 |",
    f"| 사람 판정 비율 (👍·👎가 달린 지적) | 0% | {pct(len(judged) / k) if k else '-'} ({len(judged)}/{k}) | 높을수록 좋음 |",
    f"| 코드 해결률 (지적 뒤 그 줄을 고침) | {pct(BASELINE['crr'])} | {pct(crr)} | 40% 이상 |",
    f"| 리뷰 요약 평균 길이 | {BASELINE['summary_len']}자 | {num(avg_len, '{:.0f}자')} | 1,000자 안팎 |",
    f"| 요약의 '문제없음' 항목 | 리뷰당 {BASELINE['no_issue_items']}개 | {num(avg_no, '리뷰당 {:.1f}개')} | 0개 |",
    f"| PR당 리뷰 실행 수 | {BASELINE['runs_per_pr']} | {num(len(runs) / len(runs_by_pr) if runs_by_pr else None, '{:.1f}')} (최대 {max(runs_by_pr.values()) if runs_by_pr else '-'}) | 1 + 요청 시 |",
    f"| 회귀 검증 (놓치지 않는지) | 6/6 | {eval_line} | 통과 유지 |",
    "",
    "코드 해결률은 작성자가 AI 에이전트면 \"지적이 맞았다\"가 아니라 \"지적을 따랐다\"일 수 있다. 정밀도는 👎 비율로 본다.",
]
if judged:
    lines += ["", "### 👎가 달린 지적", ""]
    lines += [f"- #{c['pr']} {c['title']} ({c['url']})" for c in comments if c["down"]] or ["없음"]
if k and not judged:
    lines += ["", "> 👍·👎가 하나도 없다. 정밀도를 재려면 리뷰 코멘트마다 맞으면 👍, 틀리면 👎를 달아 주세요."]

open(OUT, "w").write("\n".join(lines) + "\n")
print("\n".join(lines))
