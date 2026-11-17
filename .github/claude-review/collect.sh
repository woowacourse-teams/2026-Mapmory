#!/usr/bin/env bash
# 회고에 쓸 데이터를 모은다. 읽기만 하므로 읽기 권한 토큰(GH_TOKEN)으로 실행한다.
# 사용법: collect.sh <owner/repo> <출력 폴더>
set -euo pipefail

REPO=$1
OUT=$2
DAYS=${RETRO_DAYS:-7}
HIT_WEEKS=${RETRO_HIT_WEEKS:-8}
BOT=github-actions[bot]
mkdir -p "$OUT"

since=$(date -u -d "-${DAYS} days" +%Y-%m-%dT%H:%M:%SZ)
hit_since=$(date -u -d "-$((HIT_WEEKS * 7)) days" +%Y-%m-%dT%H:%M:%SZ)
date -u +%Y-%m-%d > "$OUT/today"

# 지난 기간 backend-develop에 머지된 PR
gh api "repos/$REPO/pulls?state=closed&base=backend-develop&sort=updated&direction=desc&per_page=100" \
  --jq "[.[] | select(.merged_at != null and .merged_at >= \"$since\") | {number, title, url: .html_url, author: .user.login, merged_at}]" \
  > "$OUT/merged.json"

# 본문이 길면 잘라 넣는다. 요약 코멘트 전체가 필요하지는 않다.
trim='def trim: if . == null then "" elif length > 1500 then .[0:1500] + " …(생략)" else . end;'

jq -c '.[]' "$OUT/merged.json" | while read -r pr; do
  n=$(jq -r '.number' <<< "$pr")
  inline=$(gh api "repos/$REPO/pulls/$n/comments?per_page=100" --paginate --jq "$trim"' [.[] | {id, in_reply_to_id, author: .user.login, path, line: (.line // .original_line), body: (.body | trim), up: .reactions["+1"], down: .reactions["-1"]}]' | jq -s 'add // []')
  reviews=$(gh api "repos/$REPO/pulls/$n/reviews?per_page=100" --paginate --jq "$trim"' [.[] | select(.body != "") | {author: .user.login, state, body: (.body | trim)}]' | jq -s 'add // []')
  conversation=$(gh api "repos/$REPO/issues/$n/comments?per_page=100" --paginate --jq "$trim"' [.[] | {author: .user.login, body: (.body | trim), up: .reactions["+1"], down: .reactions["-1"]}]' | jq -s 'add // []')
  commits=$(gh api "repos/$REPO/pulls/$n/commits?per_page=100" --paginate --jq '[.[] | {sha: .sha[0:7], message: (.commit.message | split("\n")[0]), date: .commit.committer.date}]' | jq -s 'add // []')
  jq -n --argjson pr "$pr" --argjson inline "$inline" --argjson reviews "$reviews" \
    --argjson conversation "$conversation" --argjson commits "$commits" \
    '$pr + {inline_comments: $inline, reviews: $reviews, conversation: $conversation, commits: $commits}'
done | jq -s '.' > "$OUT/prs.json"

# 지난 8주 Claude 리뷰 코멘트의 규칙 ID 적중 횟수
{
  gh api "repos/$REPO/pulls/comments?since=$hit_since&per_page=100" --paginate \
    --jq ".[] | select(.user.login == \"$BOT\") | .body"
  gh api "repos/$REPO/issues/comments?since=$hit_since&per_page=100" --paginate \
    --jq ".[] | select(.user.login == \"$BOT\") | .body"
} | grep -oE 'R-[0-9]{3}' | sort | uniq -c \
  | awk 'BEGIN { printf "{" } { printf "%s\"%s\": %d", (NR > 1 ? ", " : ""), $2, $1 } END { print "}" }' \
  > "$OUT/rule-hits.json"

echo "머지된 PR $(jq length "$OUT/prs.json")개, 규칙 적중 $(cat "$OUT/rule-hits.json")"
