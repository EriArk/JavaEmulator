#!/usr/bin/env bash
# SPDX-License-Identifier: GPL-3.0-only
#
# Prepare a *local-only* pinned Melange research checkout. This script never
# commits or pushes upstream code, SDK headers, ROMs or proprietary libraries.
set -euo pipefail

repo_root="$(cd "$(dirname "${BASH_SOURCE[0]}")/../../.." && pwd)"
worktree="$repo_root/engines/brew/melange-upstream"
url="https://gitlab.com/usernameak/melange.git"
revision="59b66aebd25fdcc8f422f55b0d56616476073bbf"

if ! command -v git >/dev/null 2>&1; then
  echo "git is required" >&2
  exit 2
fi
if [[ -e "$worktree" && ! -d "$worktree/.git" ]]; then
  echo "Refusing to modify unexpected path: $worktree" >&2
  exit 2
fi
if [[ ! -d "$worktree/.git" ]]; then
  echo "Cloning upstream Melange into an ignored local directory..."
  git clone --no-checkout "$url" "$worktree"
fi

git -C "$worktree" fetch origin "$revision"
if ! git -C "$worktree" show-ref --verify --quiet refs/heads/abyssme-local-experiment; then
  git -C "$worktree" switch --detach "$revision"
  git -C "$worktree" switch -c abyssme-local-experiment
else
  echo "Keeping existing local experimental branch and any uncommitted work."
  git -C "$worktree" switch abyssme-local-experiment
fi

echo
echo "Local Melange checkout: $worktree"
echo "Current revision: $(git -C "$worktree" rev-parse --short HEAD)"
echo "Local research branch: abyssme-local-experiment"
echo
echo "WARNING: Melange's main sources lack an explicit redistribution license."
echo "Do not push this checkout or copy its code into public AbyssME branches"
echo "until the author grants GPLv3-compatible redistribution permission."
echo "Missing Qualcomm BREW runtime libraries/SDK headers are NOT included."
