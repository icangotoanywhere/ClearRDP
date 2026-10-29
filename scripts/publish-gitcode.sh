#!/usr/bin/env bash
# Publish the same release on GitCode (mirror of this repository) and attach the APKs.
#
#   publish-gitcode.sh <tag> <commit-sha> <file>...
#
# Needs GITCODE_TOKEN (personal access token) and GITCODE_REPO (owner/repo).
# Safe to run repeatedly: an existing release is reused and files already attached
# are skipped.
set -euo pipefail

TAG=$1; SHA=$2; shift 2
API="https://api.gitcode.com/api/v5/repos/${GITCODE_REPO}"
AUTH="access_token=${GITCODE_TOKEN}"

# GET/POST helper: prints the body, returns non-zero on HTTP errors
call() {
  local method=$1 url=$2; shift 2
  local out code
  out=$(mktemp)
  code=$(curl -sS --connect-timeout 20 --max-time 60 -o "$out" -w '%{http_code}' -X "$method" "$url" "$@") || { rm -f "$out"; return 1; }
  cat "$out"; rm -f "$out"
  [ "$code" -ge 200 ] && [ "$code" -lt 300 ]
}

# 1. The mirror pulls from GitHub on a schedule, so the commit may not be there yet.
#    Wait for it (up to ~30 min) so the release tag points at the right commit.
for i in $(seq 1 60); do
  if call GET "${API}/commits/${SHA}?${AUTH}" >/dev/null 2>&1; then
    echo "GitCode has commit ${SHA}"
    break
  fi
  if [ "$i" = 60 ]; then
    echo "::warning::GitCode mirror has not synced ${SHA} yet; GitCode release skipped. It will be retried on the next push."
    exit 0
  fi
  echo "Waiting for the GitCode mirror to sync ${SHA} ... ($i/60)"
  sleep 30
done

# 2. Create the release if it does not exist yet
if REL=$(call GET "${API}/releases/tags/${TAG}?${AUTH}" 2>/dev/null) && [ -n "$(echo "$REL" | jq -r '.tag_name // empty' 2>/dev/null)" ]; then
  echo "GitCode release ${TAG} exists"
else
  BODY=$(jq -n --arg t "$TAG" --arg sha "$SHA" '{
    tag_name: $t,
    name: ("无遮远程 ClearRDP " + $t),
    body: ("无遮远程 ClearRDP " + $t + "\n\n下载下面的 ClearRDP-" + $t + ".apk 安装即可（Android 10 及以上，64 位手机）。\n本页与 GitHub 同步发布：https://github.com/icangotoanywhere/ClearRDP"),
    target_commitish: $sha,
    release_status: "latest"
  }')
  call POST "${API}/releases?${AUTH}" -H 'Content-Type: application/json' -d "$BODY" >/dev/null
  echo "Created GitCode release ${TAG}"
  REL=$(call GET "${API}/releases/tags/${TAG}?${AUTH}")
fi

# 3. Attach each file unless it is already there
HAVE=$(echo "$REL" | jq -r '[.assets[]?.name] | join("\n")' 2>/dev/null || true)
for f in "$@"; do
  name=$(basename "$f")
  if printf '%s\n' "$HAVE" | grep -qx "$name"; then
    echo "  $name already attached"
    continue
  fi
  UP=$(call GET "${API}/releases/${TAG}/upload_url?${AUTH}&file_name=${name}")
  url=$(echo "$UP" | jq -r '.url')
  hdrs=()
  while IFS= read -r h; do hdrs+=(-H "$h"); done < <(echo "$UP" | jq -r '.headers | to_entries[] | "\(.key): \(.value)"')
  # no "Expect: 100-continue" handshake (object storage may never answer it), hard
  # time limits so a stalled cross-border upload fails fast instead of hanging
  ok=
  for try in 1 2 3; do
    resp=$(mktemp)
    t0=$(date +%s)
    code=$(curl -sS -H 'Expect:' --connect-timeout 30 --max-time 600 \
                -o "$resp" -w '%{http_code}' -X PUT "${hdrs[@]}" --data-binary "@$f" "$url" || echo 000)
    echo "  $name: try $try -> HTTP $code in $(( $(date +%s) - t0 ))s"
    if [ "$code" -ge 200 ] && [ "$code" -lt 300 ]; then ok=1; rm -f "$resp"; break; fi
    head -c 500 "$resp"; echo; rm -f "$resp"
    sleep 10
    # a fresh signed URL for the retry
    UP=$(call GET "${API}/releases/${TAG}/upload_url?${AUTH}&file_name=${name}")
    url=$(echo "$UP" | jq -r '.url')
    hdrs=()
    while IFS= read -r h; do hdrs+=(-H "$h"); done < <(echo "$UP" | jq -r '.headers | to_entries[] | "\(.key): \(.value)"')
  done
  if [ -z "$ok" ]; then
    echo "::error::GitCode upload of $name failed"
    exit 1
  fi
done
echo "GitCode: https://gitcode.com/${GITCODE_REPO}/releases"
