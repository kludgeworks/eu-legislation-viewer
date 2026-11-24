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

# The newest of the workflow's runs that has not ended: queued, waiting or in progress
running_run() {
	gh run list --repo "$1" --workflow "$workflow" --limit 20 --json databaseId,status \
		--jq '[.[] | select(.status != "completed")][0].databaseId // empty'
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

# Follows the run until it ends, and fails if the run fails. gh run watch also fails when one of
# its own calls to GitHub fails, a 401 for a check run's annotations for example, while the run
# goes on: then the run's own state decides. A run that ended reports how it ended. A run that
# has not ended is followed again, a few times.
follow() {
	local repo=$1 run=$2 url state attempts=0
	url="https://github.com/$repo/actions/runs/$run"
	until gh run watch "$run" --repo "$repo" --exit-status; do
		state=$(gh run view "$run" --repo "$repo" --json status,conclusion --jq '"\(.status) \(.conclusion)"') ||
			die "could not read the release's run" run "$url"
		case $state in
		"completed success") return 0 ;;
		completed*) die "the release failed" conclusion "${state#completed }" run "$url" ;;
		esac
		attempts=$((attempts + 1))
		((attempts < 5)) || die "lost the release's run while following it" run "$url"
		warn "lost the release's run while following it: following it again" run "$url"
	done
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
	run=$(running_run "$repo")
	if [[ -n "$run" ]]; then
		info "A release is running already, so none is started" run "https://github.com/$repo/actions/runs/$run"
		if [[ ${usage_watch:-true} = false ]] || ! confirm "Follow it?"; then
			return 0
		fi
		follow "$repo" "$run"
		return
	fi
	before=$(dispatched_runs "$repo")
	gh workflow run "$workflow" --repo "$repo" --ref main
	run=$(new_run "$repo" "$before") ||
		die "started the Release workflow, but could not find its run" runs "https://github.com/$repo/actions/workflows/$workflow"
	info "Started the Release workflow" run "https://github.com/$repo/actions/runs/$run"
	if [[ ${usage_watch:-true} = false ]]; then
		return 0
	fi
	follow "$repo" "$run"
}

if [[ ${BASH_SOURCE[0]} = "$0" ]]; then
	main "$@"
fi
