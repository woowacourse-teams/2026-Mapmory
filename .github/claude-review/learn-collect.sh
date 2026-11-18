#!/usr/bin/env bash
# CodeRabbit 리뷰 하나와, 같은 PR의 Claude 리뷰를 모은다. 읽기만 하므로 읽기 권한 토큰(GH_TOKEN)으로 실행한다.
# 사용법: learn-collect.sh <owner/repo> <PR 번호> <CodeRabbit 리뷰 ID> <출력 폴더>
set -euo pipefail

REPO=$1
PR=$2
REVIEW_ID=$3
OUT=$4
BOT=github-actions[bot]
mkdir -p "$OUT"

# HTML 태그를 지우고 길면 자른다. CodeRabbit 본문은 아이콘·접힘 태그가 많다.
clean='def clean($n): if . == null then "" else gsub("<!--[\\s\\S]*?-->"; "") | gsub("<[^>]+>"; "") | gsub("\n{3,}"; "\n\n") | if length > $n then .[0:$n] + " …(생략)" else . end end;'

gh api "repos/$REPO/pulls/$PR" \
  --jq '{number, title, url: .html_url, author: .user.login, head: .head.sha, base: .base.ref, body: ((.body // "") | .[0:3000])}' \
  > "$OUT/pr.json"

gh api "repos/$REPO/pulls/$PR/reviews/$REVIEW_ID" \
  --jq "$clean"' {id, commit: .commit_id, submitted_at, body: (.body | clean(8000))}' \
  > "$OUT/coderabbit-review.json"

gh api "repos/$REPO/pulls/$PR/reviews/$REVIEW_ID/comments?per_page=100" --paginate \
  --jq "$clean"' [.[] | {id, path, line: (.original_line // .line), start_line: (.original_start_line // .start_line), commit: .original_commit_id, body: (.body | clean(4000))}]' \
  | jq -s 'add // []' > "$OUT/coderabbit-comments.json"

# 이 PR에 Claude 리뷰가 단 인라인 코멘트(답글 제외)와 요약
gh api "repos/$REPO/pulls/$PR/comments?per_page=100" --paginate \
  --jq "$clean"' [.[] | select(.user.login == "'"$BOT"'" and .in_reply_to_id == null) | {path, line: (.original_line // .line), commit: .original_commit_id, created_at, body: (.body | clean(3000))}]' \
  | jq -s 'add // []' > "$OUT/claude-comments.json"

gh api "repos/$REPO/issues/$PR/comments?per_page=100" --paginate \
  --jq "$clean"' [.[] | select(.user.login == "'"$BOT"'") | {updated_at, body: (.body | clean(6000))}]' \
  | jq -s 'add // [] | last // {}' > "$OUT/claude-summary.json"

# 작성자가 CodeRabbit 지적에 단 답글. 실제 문제였는지 판단하는 근거로 쓴다.
gh api "repos/$REPO/pulls/$PR/comments?per_page=100" --paginate \
  --jq "$clean"' [.[] | select(.in_reply_to_id != null) | {in_reply_to_id, author: .user.login, body: (.body | clean(1500))}]' \
  | jq -s 'add // []' > "$OUT/replies.json"

echo "CodeRabbit 지적 $(jq length "$OUT/coderabbit-comments.json")개, Claude 지적 $(jq length "$OUT/claude-comments.json")개"
