#!/usr/bin/env bash
# backend-develop에 들어온 변경에서 도메인 동기화 입력을 만든다.
# 사용: sync-collect.sh <저장소> <이전 커밋> <이후 커밋> <출력 디렉터리>
# 출력: adr.diff, signals.md, commits.md, prs.txt(이 범위에서 머지된 PR 번호). 신호가 하나도 없으면 아무것도 만들지 않는다.
set -uo pipefail

repo="$1"; before="$2"; after="$3"; out="$4"
mkdir -p "$out"

# 새 브랜치 push(before가 0)이거나 이전 커밋이 얕은 클론에 없으면 직전 커밋과 비교한다.
if [ -z "$before" ] || [ "$before" = "0000000000000000000000000000000000000000" ] || ! git cat-file -e "$before^{commit}" 2>/dev/null; then
  before=$(git rev-parse "$after^1")
fi
range="$before..$after"
echo "비교 범위: $range"

git diff "$before" "$after" -- backend/docs/adr > "$out/adr.diff"

java_files=$(git diff --name-only "$before" "$after" -- backend/src/main/java || true)
signals=""
add_signal() { signals+="| $1 | \`$2\` |"$'\n'; signal_files+=("$2"); }
signal_files=()

for f in $(git diff --name-only --diff-filter=A "$before" "$after" -- backend/src/main/resources/db/migration || true); do
  add_signal "새 마이그레이션" "$f"
done
for f in $java_files; do
  added=$(git diff -U0 "$before" "$after" -- "$f" | grep -E '^\+[^+]' || true)
  [ -z "$added" ] && continue
  if grep -qE 'ForUpdate|ForShare|@Lock|FOR UPDATE|FOR SHARE|SKIP LOCKED|PESSIMISTIC' <<<"$added"; then
    add_signal "잠금" "$f"
  elif grep -qE 'publishEvent|@(Transactional)?EventListener|implements MemberDataPurger' <<<"$added"; then
    add_signal "이벤트·파기" "$f"
  elif [[ "$f" == *ErrorCode.java ]]; then
    add_signal "오류 코드" "$f"
  elif git diff --name-only --diff-filter=A "$before" "$after" -- "$f" | grep -qE '(Event|Deleted|Withdrawn|Abandoned|Created)\.java$'; then
    add_signal "새 이벤트" "$f"
  fi
done

if [ ! -s "$out/adr.diff" ] && [ ${#signal_files[@]} -eq 0 ]; then
  echo "ADR 변경과 코드 신호가 없다."
  rm -f "$out/adr.diff"
  exit 0
fi

{
  echo "# 코드 신호"
  echo
  if [ ${#signal_files[@]} -eq 0 ]; then
    echo "없음"
  else
    echo "| 종류 | 파일 |"
    echo "|---|---|"
    printf '%s' "$signals"
    echo
    for f in "${signal_files[@]}"; do
      echo "## \`$f\`"
      echo
      echo '```diff'
      git diff "$before" "$after" -- "$f" | head -400
      echo '```'
      echo
    done
  fi
} > "$out/signals.md"

{
  echo "# 이번에 들어온 커밋과 PR"
  echo
  git log --format='- %h %s' "$range" | head -100
  echo
  # 머지 커밋에 연결된 PR의 설명. 팀 결정이 적혀 있을 수 있다.
  # 이 커밋을 포함한 열린 PR도 함께 나온다. 이 커밋으로 머지된 PR만 남긴다.
  : > "$out/prs.txt"
  for sha in $(git log --merges --format='%H' "$range" | head -20); do
    pr=$(gh api "repos/$repo/commits/$sha/pulls" --jq ".[] | select(.merge_commit_sha == \"$sha\")" 2>/dev/null || true)
    [ -z "$pr" ] && continue
    jq -r '.number' <<<"$pr" >> "$out/prs.txt"
    jq -r '"## #\(.number) \(.title)\n\n\((.body // "") | .[0:4000])\n"' <<<"$pr"
  done
} > "$out/commits.md"

echo "ADR diff $(wc -l < "$out/adr.diff")줄, 코드 신호 ${#signal_files[@]}개"
