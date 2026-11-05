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
set -euo pipefail

root=$(cd "$(dirname "$0")/../.." && pwd)
cd "$root"
migrations=viewer/src/main/resources/db/migration
checker=tools/tasks/migration/check.bash

die() {
	echo "release: $*" >&2
	exit 1
}

latest_tag() {
	git tag --list | grep -E '^v20[0-9]{2}\.(0[1-9]|1[0-2])\.[0-9]+$' |
		sort -V | tail -n 1 || true
}

next_tag() {
	local month latest micro=0
	month=$(TZ=Europe/Amsterdam date +%Y.%m)
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
	local version=$1 old new module previous_module='' sequence=0 description build
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
		if [[ ${usage_dry_run:-false} = true ]]; then
			echo "git mv $old $new"
			continue
		fi
		git mv "$old" "$new"
		while IFS= read -r build; do
			[[ -n "$build" ]] || continue
			perl -0pi -e "s{\Q$old\E}{$new}g" "$build"
		done < <(rg -l -F -g 'BUILD.bazel' "$old" . || true)
	done < <(pending_migrations)
}

require_release_head
"$checker"
tag=$(next_tag)
version=${tag#v}
latest=$(latest_tag)
if [[ -n "$latest" && $(printf '%s\n%s\n' "$latest" "$tag" | sort -V | tail -n 1) != "$tag" ]]; then
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
bazel test //...

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
