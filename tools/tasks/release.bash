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

# The workflow releases origin's main, whatever is checked out here. Commits on main here that
# origin doesn't have are not in it: a warning, so that no one takes them to be.
warn_unpushed() {
	local unpushed
	unpushed=$(git rev-list --count origin/main..main 2>/dev/null || echo 0)
	((unpushed == 0)) ||
		warn "main here has commits that origin's doesn't: they won't be in the release" commits "$unpushed" push "git push origin main"
}

# What the workflow would release: origin's main prepared in a worktree of its own, then removed.
# The checkout here, its branch and its changes, play no part.
dry_run() {
	local scratch
	scratch=$(mktemp -d "${TMPDIR:-/tmp}/release-dry-run.XXXXXX")
	git worktree add --quiet --detach "$scratch/main" origin/main
	# shellcheck disable=SC2064 # $scratch now, not when the trap runs
	trap "git worktree remove --force '$scratch/main'; rm -rf '$scratch'" EXIT
	REPO_ROOT="$scratch/main" usage_dry_run=true "$prepare"
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
	git fetch --quiet --tags origin
	if [[ ${usage_dry_run:-false} = true ]]; then
		dry_run
		return 0
	fi
	warn_unpushed
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
