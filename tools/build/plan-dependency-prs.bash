#!/usr/bin/env bash
# SPDX-License-Identifier: MIT
set -euo pipefail

branches=$(gh api --paginate --slurp "repos/$GITHUB_REPOSITORY/pulls?base=main&state=open&per_page=100" |
	jq -c '[.[][] | select(.head.repo.full_name == .base.repo.full_name) | .head.ref]')
matrix=$(python3 tools/build/upgrade_dependencies.py plan "$branches")
printf 'matrix=%s\n' "$matrix" >>"$GITHUB_OUTPUT"
