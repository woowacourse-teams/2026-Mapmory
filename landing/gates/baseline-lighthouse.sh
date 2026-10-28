#!/bin/bash
# The page without this section, measured the way 5.2/5.3 measure the page with it: builds the branch's merge-base
# with main in a git worktree, serves it on :4174 and runs the same Lighthouse command. Writes
# gates/reports/evidence/stage-5/baseline-lighthouse.json and baseline.json, which 5.2/5.3 then quote as "기준선".
#   bash gates/baseline-lighthouse.sh            (from landing/, after npm ci; needs origin/main fetched)
set -e
LANDING=$(cd "$(dirname "$0")/.." && pwd)
ROOT=$(cd "$LANDING/.." && pwd)
BASE=$(git -C "$ROOT" merge-base HEAD origin/main)
WT=$(mktemp -d)/baseline
EV="$LANDING/gates/reports/evidence/stage-5"
mkdir -p "$EV"
git -C "$ROOT" worktree add -f "$WT" "$BASE" >/dev/null
trap 'pkill -f "vite preview --port 4174" >/dev/null 2>&1; git -C "$ROOT" worktree remove --force "$WT"' EXIT
ln -s "$LANDING/node_modules" "$WT/landing/node_modules"
(cd "$WT/landing" && npm run build >/dev/null 2>&1)
(cd "$WT/landing" && nohup npx vite preview --port 4174 --strictPort >/dev/null 2>&1 &)
curl -s --retry 20 --retry-connrefused --retry-delay 1 -o /dev/null http://localhost:4174/
CHROME_PATH=${GATES_CHROME:-$(node -e 'console.log(require("playwright").chromium.executablePath())')} \
  node "$LANDING/node_modules/lighthouse/cli/index.js" http://localhost:4174/ --output=json --output-path="$EV/baseline-lighthouse.json" --quiet \
  --form-factor=mobile --only-categories=performance,accessibility,best-practices "--chrome-flags=--headless=new --no-sandbox --disable-gpu"
printf '{\n  "ref": "main %s (이 브랜치의 merge-base) 빌드",\n  "measuredAt": "%s",\n  "how": "gates/baseline-lighthouse.sh: git worktree로 merge-base를 받아 npm run build, vite preview --port 4174, 5.2와 같은 Lighthouse 명령"\n}\n' \
  "$(git -C "$ROOT" rev-parse --short "$BASE")" "$(date -u +%Y-%m-%dT%H:%M:%SZ)" > "$EV/baseline.json"
echo "baseline: $(node -e 'const l=require(process.argv[1]);console.log(Object.entries(l.categories).map(([k,v])=>k+" "+Math.round(v.score*100)).join(", "), "LCP", l.audits["largest-contentful-paint"].displayValue, "TBT", l.audits["total-blocking-time"].displayValue)' "$EV/baseline-lighthouse.json")"
