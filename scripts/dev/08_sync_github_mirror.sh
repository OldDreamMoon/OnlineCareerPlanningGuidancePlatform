#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
MIRROR_DIR_DEFAULT="$(cd "$ROOT/.." && pwd)/$(basename "$ROOT")-github"
MIRROR_REMOTE_URL_DEFAULT="git@github.com:OldDreamMoon/OnlineCareerPlanningGuidancePlatform.git"

MIRROR_DIR="${MIRROR_DIR:-$MIRROR_DIR_DEFAULT}"
MIRROR_BRANCH="${MIRROR_BRANCH:-main}"
MIRROR_REMOTE_URL="${MIRROR_REMOTE_URL:-$MIRROR_REMOTE_URL_DEFAULT}"
COMMIT_MESSAGE="${COMMIT_MESSAGE:-}"
DO_PUSH=0

is_blank_message() {
  local message="${1:-}"
  [ -z "${message//[[:space:]]/}" ]
}

is_dangerous_mirror_dir() {
  local dir="$1"
  case "$dir" in
    ""|"/"|"$HOME"|"$ROOT"|"$ROOT/"|"/home"|"/home/olddream"|"/home/olddream/code")
      return 0
      ;;
  esac
  return 1
}

usage() {
  cat <<'USAGE'
Usage:
  bash scripts/dev/08_sync_github_mirror.sh [options]

Options:
  --mirror-dir <path>   Mirror repo path (default: ../<repo>-github)
  --remote <url>        Set/check mirror repo origin URL
  --branch <name>       Branch for mirror repo (default: main)
  --push                Push after commit
  --commit-message <m>  Commit message for mirror repo (default: latest local commit message)
  -h, --help            Show help

Environment variables (optional):
  MIRROR_DIR, MIRROR_BRANCH, MIRROR_REMOTE_URL, COMMIT_MESSAGE (optional override)

Default mirror remote:
  git@github.com:OldDreamMoon/OnlineCareerPlanningGuidancePlatform.git
USAGE
}

while [ "$#" -gt 0 ]; do
  case "$1" in
    --mirror-dir)
      MIRROR_DIR="$2"
      shift 2
      ;;
    --remote)
      MIRROR_REMOTE_URL="$2"
      shift 2
      ;;
    --branch)
      MIRROR_BRANCH="$2"
      shift 2
      ;;
    --push)
      DO_PUSH=1
      shift
      ;;
    --commit-message)
      COMMIT_MESSAGE="$2"
      shift 2
      ;;
    -h|--help)
      usage
      exit 0
      ;;
    *)
      echo "[ERROR] Unknown arg: $1" >&2
      usage
      exit 1
      ;;
  esac
done

MIRROR_DIR="$(mkdir -p "$MIRROR_DIR" && cd "$MIRROR_DIR" && pwd)"

if is_dangerous_mirror_dir "$MIRROR_DIR"; then
  echo "[ERROR] Refusing dangerous mirror directory: $MIRROR_DIR" >&2
  exit 1
fi

if [ ! -d "$ROOT/.git" ]; then
  echo "[ERROR] Source is not a git repository: $ROOT" >&2
  exit 1
fi

echo "[INFO] Source repo: $ROOT"
echo "[INFO] Mirror repo: $MIRROR_DIR"
echo "[INFO] Mirror branch: $MIRROR_BRANCH"
echo "[INFO] Mirror remote: $MIRROR_REMOTE_URL"

if is_blank_message "$COMMIT_MESSAGE"; then
  COMMIT_MESSAGE="$(git -C "$ROOT" log -1 --pretty=%B 2>/dev/null || true)"
fi

if is_blank_message "$COMMIT_MESSAGE"; then
  COMMIT_MESSAGE="sync: $(date '+%Y-%m-%d %H:%M:%S')"
fi

if [ ! -d "$MIRROR_DIR/.git" ]; then
  git -C "$MIRROR_DIR" init -b "$MIRROR_BRANCH" >/dev/null
  echo "[INIT] Created mirror git repo: $MIRROR_DIR"
fi

if git -C "$MIRROR_DIR" remote get-url origin >/dev/null 2>&1; then
  existing_url="$(git -C "$MIRROR_DIR" remote get-url origin)"
  if [ "$existing_url" != "$MIRROR_REMOTE_URL" ]; then
    git -C "$MIRROR_DIR" remote set-url origin "$MIRROR_REMOTE_URL"
    echo "[INIT] Updated mirror origin: $MIRROR_REMOTE_URL"
  fi
else
  git -C "$MIRROR_DIR" remote add origin "$MIRROR_REMOTE_URL"
  echo "[INIT] Set mirror origin: $MIRROR_REMOTE_URL"
fi

if [ -n "$(git -C "$MIRROR_DIR" status --porcelain --untracked-files=no 2>/dev/null || true)" ]; then
  echo "[WARN] Mirror repo has tracked local changes; they will be replaced by whitelist sync." >&2
fi

if ! git -C "$MIRROR_DIR" rev-parse --verify "$MIRROR_BRANCH" >/dev/null 2>&1; then
  git -C "$MIRROR_DIR" checkout --orphan "$MIRROR_BRANCH" >/dev/null 2>&1 || true
else
  git -C "$MIRROR_DIR" checkout "$MIRROR_BRANCH" >/dev/null
fi

list_file="$(mktemp)"
trap 'rm -f "$list_file"' EXIT

git -C "$ROOT" ls-files -- \
  .github \
  README.md \
  Makefile \
  mise.toml \
  .gitignore \
  .env.example \
  apps/web \
  apps/server-java \
  apps/interview-live-python \
  infra \
  scripts/dev \
  scripts/db \
  scripts/deploy \
  tests \
  docs/spec > "$list_file"

if [ ! -s "$list_file" ]; then
  echo "[ERROR] Whitelist produced an empty file list" >&2
  exit 1
fi

echo "[INFO] Replacing mirror working tree with whitelisted tracked files..."
find "$MIRROR_DIR" -mindepth 1 -maxdepth 1 ! -name '.git' -exec rm -rf {} +

while IFS= read -r rel_path; do
  src="$ROOT/$rel_path"
  dst="$MIRROR_DIR/$rel_path"
  mkdir -p "$(dirname "$dst")"
  cp -p "$src" "$dst"
done < "$list_file"

forbidden_paths=(
  ".agent_system"
  ".agent_tracking"
  "AGENTS.md"
  "data"
  "plan"
  "scripts/agent"
  "docs/source"
  "docs/operations"
  "docs/setup"
)

for p in "${forbidden_paths[@]}"; do
  if [ -e "$MIRROR_DIR/$p" ]; then
    echo "[ERROR] Forbidden path leaked into mirror: $p" >&2
    exit 1
  fi
done

if find "$MIRROR_DIR" -name '.env' -type f | grep -q .; then
  echo "[ERROR] .env file found in mirror repo" >&2
  exit 1
fi

git -C "$MIRROR_DIR" add -A

if git -C "$MIRROR_DIR" diff --cached --quiet; then
  echo "[SKIP] No mirror changes to commit"
else
  git -C "$MIRROR_DIR" commit -m "$COMMIT_MESSAGE"
  echo "[DONE] Mirror commit created"
fi

if [ "$DO_PUSH" -eq 1 ]; then
  if ! git -C "$MIRROR_DIR" remote get-url origin >/dev/null 2>&1; then
    echo "[ERROR] Mirror origin is not configured." >&2
    exit 1
  fi
  git -C "$MIRROR_DIR" push -u origin "$MIRROR_BRANCH"
  echo "[DONE] Mirror pushed: origin/$MIRROR_BRANCH"
fi
