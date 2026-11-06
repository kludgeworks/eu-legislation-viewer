#!/usr/bin/env bash
# SPDX-License-Identifier: MIT
#MISE description="Tag the next CalVer release, signed, and push it to publish it"
#MISE alias="tag"
#MISE quiet=true
#MISE depends=["release:db-migrations"]
#MISE raw=true
#USAGE include file="$MISE_CONFIG_ROOT/tools/tasks/release.usage.kdl"
# Bash 3.2+ only: that includes the Bash shipped with supported macOS versions.
set -euo pipefail

ui_prefix=release
# shellcheck source=tools/tasks/lib/ui.bash
source "$(dirname "${BASH_SOURCE[0]}")/lib/ui.bash"

root=${REPO_ROOT:-$(cd "$(dirname "$0")/../.." && pwd)}
cd "$root"
migrations=viewer/src/main/resources/db/migration
checker=tools/tasks/release/db-migrations.bash

latest_tag() {
	git tag --list --sort=version:refname | grep -E '^v20[0-9]{2}\.(0[1-9]|1[0-2])\.[0-9]+$' |
		tail -n 1 || true
}

is_later_than() {
	local candidate=${1#v} latest=${2#v} candidate_year candidate_month candidate_micro
	local latest_year latest_month latest_micro
	IFS=. read -r candidate_year candidate_month candidate_micro <<EOF
$candidate
EOF
	IFS=. read -r latest_year latest_month latest_micro <<EOF
$latest
EOF
	((10#$candidate_year > 10#$latest_year)) ||
		((10#$candidate_year == 10#$latest_year && 10#$candidate_month > 10#$latest_month)) ||
		((10#$candidate_year == 10#$latest_year && 10#$candidate_month == 10#$latest_month && 10#$candidate_micro > 10#$latest_micro))
}

next_tag() {
	local month latest micro=0
	month=${RELEASE_MONTH:-$(TZ=Europe/Amsterdam date +%Y.%m)}
	latest=$(latest_tag)
	while IFS= read -r tag; do
		[[ "$tag" =~ ^v${month//./\.}\.([0-9]+)$ ]] || continue
		((BASH_REMATCH[1] > micro)) && micro=${BASH_REMATCH[1]}
	done < <(git tag --list "v$month.*")
	if [[ -n "$latest" && "$latest" =~ ^v$month\. ]]; then
		micro=$((micro + 1))
	fi
	echo "v$month.$micro"
}

require_clean_main() {
	[[ $(git branch --show-current) = main ]] || die "release from main, not $(git branch --show-current)"
	[[ -z $(git status --porcelain) ]] || die "the working tree has changes: commit or stash them first"
	# --no-prune: fetch.pruneTags would delete a release tagged here but not pushed yet
	git fetch --quiet --tags --no-prune origin
}

require_origin_main() {
	git diff --quiet HEAD origin/main || die "main differs from origin/main: pull or push it first"
}

# HEAD is what the tag released: nothing to release
unchanged_since() {
	git diff --quiet "$1" HEAD
}

# origin has the tag, as it is here, and main up to HEAD
on_origin() {
	local tag=$1 remote
	remote=$(git ls-remote origin "refs/tags/$tag" | cut -f1)
	[[ "$remote" = "$(git rev-parse "refs/tags/$tag")" ]] && git merge-base --is-ancestor HEAD origin/main
}

# --push or --no-push, else the answer on the terminal; no terminal (CI), no push
wants_push() {
	local tag=$1
	case ${usage_push-} in
	true) return 0 ;;
	false) return 1 ;;
	esac
	confirm "Publish $tag (push main and its tag to origin)?"
}

# where origin's GitHub Actions runs are, when origin is on GitHub
workflow_runs() {
	local url
	url=$(git remote get-url origin)
	[[ "$url" =~ github\.com[:/](.+/[^/]+)$ ]] || return 0
	echo "https://github.com/${BASH_REMATCH[1]%.git}/actions/workflows/release.yml"
}

push() {
	local runs
	git push --quiet origin main "$1"
	runs=$(workflow_runs)
	if [[ -n "$runs" ]]; then
		info "Published: the Release workflow builds and publishes it" version "$1" runs "$runs"
	else
		info "Published: the Release workflow builds and publishes it" version "$1"
	fi
}

not_pushed() {
	warn "Kept local, not published" version "$1" publish "mise run release --push"
}

# The latest release, unchanged since but not on origin: push it, as asked
push_unpushed() {
	local tag=$1
	if on_origin "$tag"; then
		info "Nothing to release: nothing changed since the latest release, which is published" version "$tag"
		return 0
	fi
	warn "Nothing changed since the latest release, but it isn't published: origin doesn't have it" version "$tag"
	[[ ${usage_dry_run:-false} = true ]] && return 0
	if wants_push "$tag"; then
		push "$tag"
	else
		not_pushed "$tag"
	fi
}

pending_migrations() {
	find "$migrations" -type f -name 'NEXT_*.sql' | LC_ALL=C sort
}

rename_migrations() {
	local version=$1 old new name
	while IFS= read -r old; do
		[[ -n "$old" ]] || continue
		name=${old##*/}
		new="${old%/*}/V${version//./_}_${name#NEXT_}"
		if [[ ${usage_dry_run:-false} = true ]]; then
			info "Would rename" from "$old" to "${new##*/}"
			continue
		fi
		git mv "$old" "$new"
		info "Renamed" from "$old" to "${new##*/}"
	done < <(pending_migrations)
}

# A step failed after the release began changing main: back to where it began, which was clean
# (require_clean_main), so --merge, which keeps unstaged work, has nothing else to keep.
undo_release() {
	local status=$? start=$1
	((status == 0)) && return
	git reset --quiet --merge "$start"
	warn "Failed, so the release is undone: main is as it was" commit "$(git rev-parse --short "$start")"
	exit "$status"
}

main() {
	local tag version latest start pushing=false
	require_clean_main
	latest=$(latest_tag)
	if [[ -n "$latest" ]] && unchanged_since "$latest"; then
		push_unpushed "$latest"
		exit 0
	fi
	require_origin_main
	tag=$(next_tag)
	version=${tag#v}
	if [[ -n "$latest" ]] && ! is_later_than "$tag" "$latest"; then
		die "computed $tag is not later than existing release $latest"
	fi

	info "Next release" version "$tag" migrations "$(pending_migrations | grep -c . || true)"
	if [[ ${usage_dry_run:-false} = true ]]; then
		rename_migrations "$version"
		exit 0
	fi

	if wants_push "$tag"; then
		pushing=true
	fi

	start=$(git rev-parse HEAD)
	# shellcheck disable=SC2064 # $start now, not when the trap runs
	trap "undo_release $start" EXIT
	info "Running every check"
	mise run check
	rename_migrations "$version"
	"$checker" --final
	info "Running every test"
	mise run test

	if ! git diff --cached --quiet; then
		git commit --quiet -m "chore(release): prepare $tag"
		info "Committed" commit "chore(release): prepare $tag"
	fi

	git tag -s "$tag" -m "Release $tag"
	trap - EXIT
	info "Tagged, signed" version "$tag"
	if [[ $pushing = true ]]; then
		push "$tag"
	else
		not_pushed "$tag"
	fi
}

if [[ ${BASH_SOURCE[0]} = "$0" ]]; then
	main "$@"
fi
