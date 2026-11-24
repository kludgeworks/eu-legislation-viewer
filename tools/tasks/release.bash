#!/usr/bin/env bash
# SPDX-License-Identifier: MIT
#MISE description="Release the next CalVer version: run the Release workflow on main and follow it"
#MISE quiet=true
#MISE raw=true
#USAGE include file="$MISE_CONFIG_ROOT/tools/tasks/release.usage.kdl"
# Bash 3.2+ only: that includes the Bash shipped with supported macOS versions.
set -euo pipefail

ui_prefix=release
# shellcheck source=tools/lib/ui.bash
source "$(dirname "${BASH_SOURCE[0]}")/../lib/ui.bash"

tasks=$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)
root=${REPO_ROOT:-$(cd "$(dirname "$0")/../.." && pwd)}
cd "$root"
prepare=$tasks/release/prepare.bash
workflow=release.yml

require_clean_main() {
	[[ $(git branch --show-current) = main ]] || die "release from main, not $(git branch --show-current)"
	[[ -z $(git status --porcelain) ]] || die "the working tree has changes: commit or stash them first"
	git fetch --quiet --tags origin
}

# The workflow releases origin's main: what is here must be that, so that it is what was seen
require_origin_main() {
	git diff --quiet HEAD origin/main || die "main differs from origin/main: pull or push it first"
}

# origin's GitHub repository (owner/name)
github_repo() {
	local url
	url=$(git remote get-url origin 2>/dev/null) || die "no origin to release from"
	[[ "$url" =~ github\.com[:/](.+/[^/]+)$ ]] || die "origin is not on GitHub: $url"
	echo "${BASH_REMATCH[1]%.git}"
}

# The ids of the workflow's runs on main that a dispatch started, newest first
dispatched_runs() {
	gh run list --repo "$1" --workflow "$workflow" --branch main --event workflow_dispatch \
		--limit 20 --json databaseId --jq '.[].databaseId'
}

# The run that the dispatch started: the newest one that was not there before it. GitHub lists a
# run a few seconds after the dispatch.
new_run() {
	local repo=$1 before=$2 run attempts=0
	while ((attempts < 10)); do
		attempts=$((attempts + 1))
		while IFS= read -r run; do
			[[ -n "$run" ]] || continue
			grep -qx "$run" <<<"$before" || {
				echo "$run"
				return 0
			}
		done < <(dispatched_runs "$repo")
		sleep "${RELEASE_POLL:-3}"
	done
	return 1
}

main() {
	local repo before run
	require_clean_main
	require_origin_main
	if [[ ${usage_dry_run:-false} = true ]]; then
		REPO_ROOT=$root usage_dry_run=true "$prepare"
		return 0
	fi
	repo=$(github_repo)
	before=$(dispatched_runs "$repo")
	gh workflow run "$workflow" --repo "$repo" --ref main
	run=$(new_run "$repo" "$before") ||
		die "started the Release workflow, but could not find its run" runs "https://github.com/$repo/actions/workflows/$workflow"
	info "Started the Release workflow" run "https://github.com/$repo/actions/runs/$run"
	if [[ ${usage_watch:-true} = false ]]; then
		return 0
	fi
	gh run watch "$run" --repo "$repo" --exit-status
}

if [[ ${BASH_SOURCE[0]} = "$0" ]]; then
	main "$@"
fi
