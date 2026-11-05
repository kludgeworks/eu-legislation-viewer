#!/usr/bin/env bash
# SPDX-License-Identifier: MIT
#MISE description="Prepare, sign, and publish the next CalVer release"
#MISE alias="tag"
#MISE quiet=true
#USAGE flag "-n --dry-run" help="Show the release version and migration renames without changing anything" env="RELEASE_DRY_RUN"
#
# This is deliberately the one exception to the repository's GitButler-only write policy: it is a
# human-operated release boundary. It uses Git only to commit the mechanical release preparation,
# create a signed annotated tag, and push main plus that tag.
# Bash 3.2+ only: that includes the Bash shipped with supported macOS versions.
set -euo pipefail

root=${REPO_ROOT:-$(cd "$(dirname "$0")/../.." && pwd)}
cd "$root"
migrations=viewer/src/main/resources/db/migration
checker=tools/tasks/migration/check.bash

die() {
	echo "release: $*" >&2
	exit 1
}

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

require_release_head() {
	[[ $(git branch --show-current) = main ]] || die "release from main, not $(git branch --show-current)"
	[[ -z $(git status --porcelain) ]] || die "working tree must be clean"
	git fetch --quiet --tags origin
	git diff --quiet HEAD origin/main || die "local main differs from origin/main"
}

pending_migrations() {
	local latest
	latest=$(latest_tag)
	while IFS= read -r path; do
		[[ -n "$path" ]] || continue
		if [[ -z "$latest" ]] || ! git cat-file -e "$latest:$path" 2>/dev/null; then
			echo "$path"
		fi
	done < <(rg --files "$migrations" -g 'V*.sql' | sort)
}

rename_migrations() {
	local version=$1 old new old_label new_label module previous_module='' sequence=0 description build
	while IFS= read -r old; do
		[[ -n "$old" ]] || continue
		module=$(dirname "$old")
		if [[ "$module" != "$previous_module" ]]; then
			previous_module=$module
			sequence=0
		fi
		sequence=$((sequence + 1))
		description=${old##*__}
		new="$module/V${version//./_}_$(printf '%03d' "$sequence")__${description}"
		old_label="//viewer/src/main/resources:${old#viewer/src/main/resources/}"
		new_label="//viewer/src/main/resources:${new#viewer/src/main/resources/}"
		if [[ ${usage_dry_run:-false} = true ]]; then
			echo "git mv $old $new"
			continue
		fi
		git mv "$old" "$new"
		while IFS= read -r build; do
			[[ -n "$build" ]] || continue
			perl -0pi -e "s{\Q$old_label\E}{$new_label}g" "$build"
		done < <(git ls-files | grep '/BUILD\.bazel$' | while IFS= read -r build; do
			grep -F -q "$old_label" "$build" && echo "$build"
		done)
	done < <(pending_migrations)
}

main() {
	local tag version latest
	require_release_head
	"$checker"
	tag=$(next_tag)
	version=${tag#v}
	latest=$(latest_tag)
	if [[ -n "$latest" ]] && ! is_later_than "$tag" "$latest"; then
		die "computed $tag is not later than existing release $latest"
	fi

	echo "Release: $tag" >&2
	if [[ ${usage_dry_run:-false} = true ]]; then
		rename_migrations "$version"
		exit 0
	fi

	rename_migrations "$version"
	"$checker" --final
	git diff --check
	mise run check
	mise run test

	if ! git diff --quiet; then
		git add -A -- "$migrations" \
			viewer/src/main/java/dev/groknull/kludgeworks/eu/legislation/viewer/catalogue/infra/BUILD.bazel \
			viewer/src/main/java/dev/groknull/kludgeworks/eu/legislation/viewer/comparison/infra/BUILD.bazel \
			viewer/src/main/java/dev/groknull/kludgeworks/eu/legislation/viewer/content/infra/BUILD.bazel \
			viewer/src/main/java/dev/groknull/kludgeworks/eu/legislation/viewer/ingest/infra/BUILD.bazel
		git commit -m "chore(release): prepare $tag"
	fi

	git tag -s "$tag" -m "Release $tag"
	git push origin main "$tag"
}

if [[ ${BASH_SOURCE[0]} = "$0" ]]; then
	main "$@"
fi
