#!/usr/bin/env bash
# SPDX-License-Identifier: MIT
#MISE description="Prepare, sign, and publish the next CalVer release"
#MISE alias="tag"
#MISE quiet=true
#MISE depends=["release:db-migrations"]
#USAGE flag "-n --dry-run" help="Show the release version and migration renames without changing anything" env="RELEASE_DRY_RUN"
#
# `mise run release` picks the next CalVer version, checks main, gives pending migrations their
# final names (NEXT_007__a.sql is V2026_11_0_007__a.sql: the number stays), tests the result,
# commits that preparation, and pushes main with a signed tag. It depends on release:db-migrations,
# so a changed released migration, or a misnamed pending one, stops it before anything else; the
# check runs before the renames, which only --final allows, and the tests after them, on what is
# released.
# Bash 3.2+ only: that includes the Bash shipped with supported macOS versions.
set -euo pipefail

root=${REPO_ROOT:-$(cd "$(dirname "$0")/../.." && pwd)}
cd "$root"
migrations=viewer/src/main/resources/db/migration
checker=tools/tasks/release/db-migrations.bash

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
	find "$migrations" -type f -name 'NEXT_*.sql' | LC_ALL=C sort
}

rename_migrations() {
	local version=$1 old new name
	while IFS= read -r old; do
		[[ -n "$old" ]] || continue
		name=${old##*/}
		new="${old%/*}/V${version//./_}_${name#NEXT_}"
		if [[ ${usage_dry_run:-false} = true ]]; then
			echo "git mv $old $new"
			continue
		fi
		git mv "$old" "$new"
	done < <(pending_migrations)
}

main() {
	local tag version latest
	require_release_head
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

	mise run check
	rename_migrations "$version"
	"$checker" --final
	mise run test

	if ! git diff --cached --quiet; then
		git commit -m "chore(release): prepare $tag"
	fi

	git tag -s "$tag" -m "Release $tag"
	git push origin main "$tag"
}

if [[ ${BASH_SOURCE[0]} = "$0" ]]; then
	main "$@"
fi
