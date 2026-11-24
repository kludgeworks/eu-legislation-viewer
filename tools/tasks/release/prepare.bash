#!/usr/bin/env bash
# SPDX-License-Identifier: MIT
#MISE description="Prepare the next CalVer release in the working tree: name its migrations, pin its image"
#MISE quiet=true
#MISE depends=["release:db-migrations"]
#USAGE include file="$MISE_CONFIG_ROOT/tools/tasks/release/prepare.usage.kdl"
# Bash 3.2+ only: that includes the Bash shipped with supported macOS versions.
set -euo pipefail

ui_prefix=release:prepare
# shellcheck source=tools/lib/ui.bash
source "$(dirname "${BASH_SOURCE[0]}")/../../lib/ui.bash"

tasks=$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)
root=${REPO_ROOT:-$(cd "$(dirname "$0")/../../.." && pwd)}
cd "$root"
migrations=viewer/src/main/resources/db/migration
deployment=deploy/k8s/base/viewer.yaml
image=ghcr.io/kludgeworks/eu-legislation-viewer
notes=$tasks/notes.bash

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

# HEAD is what the tag released: nothing to release
unchanged_since() {
	git diff --quiet "$1" HEAD
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

# The deployment's image at the release's version: its one image: line, the tag replaced
pin_image() {
	local version=$1
	[[ $(grep -Fc "image: $image:" "$deployment" || true) = 1 ]] ||
		die "$deployment has no image $image to pin, or more than one"
	if [[ ${usage_dry_run:-false} = true ]]; then
		info "Would pin the image" version "$version" in "$deployment"
		return
	fi
	sed -E "s#(image: $image:).*\$#\1$version#" "$deployment" >"$deployment.pinning"
	mv "$deployment.pinning" "$deployment"
	git add "$deployment"
	info "Pinned the image" version "$version" in "$deployment"
}

# origin's GitHub repository (owner/name), when origin is on GitHub
github_repo() {
	local url
	url=$(git remote get-url origin 2>/dev/null) || return 0
	[[ "$url" =~ github\.com[:/](.+/[^/]+)$ ]] || return 0
	echo "${BASH_REMATCH[1]%.git}"
}

# The release's notes, as its GitHub release will show them, rendered on a terminal
show_notes() {
	REPO_ROOT=$root usage_tag=$1 usage_repo=$(github_repo) "$notes" | markdown
}

# name=value, for the workflow's later steps (GITHUB_OUTPUT), when --output names a file
emit() {
	[[ -n "${usage_output:-}" ]] || return 0
	printf '%s=%s\n' "$1" "$2" >>"$usage_output"
}

main() {
	local tag version latest
	[[ -z $(git status --porcelain) ]] || die "the working tree has changes: prepare a release from a clean checkout"
	latest=$(latest_tag)
	if [[ -n "$latest" ]] && unchanged_since "$latest"; then
		info "Nothing to release: nothing changed since the latest release" version "$latest"
		emit tag ""
		return 0
	fi
	tag=$(next_tag)
	version=${tag#v}
	if [[ -n "$latest" ]] && ! is_later_than "$tag" "$latest"; then
		die "computed $tag is not later than existing release $latest"
	fi

	info "Next release" version "$tag" migrations "$(pending_migrations | grep -c . || true)"
	rename_migrations "$version"
	pin_image "$version"
	if [[ ${usage_dry_run:-false} = true ]]; then
		show_notes "$tag"
		return 0
	fi
	emit tag "$tag"
	emit version "$version"
	emit tree "$(git write-tree)"
}

if [[ ${BASH_SOURCE[0]} = "$0" ]]; then
	main "$@"
fi
