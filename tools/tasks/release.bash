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

require_clean_main() {
	[[ $(git branch --show-current) = main ]] || die "release from main, not $(git branch --show-current)"
	[[ -z $(git status --porcelain) ]] || die "working tree must be clean"
	# --no-prune: fetch.pruneTags would delete a release tagged here but not pushed yet
	git fetch --quiet --tags --no-prune origin
}

require_origin_main() {
	git diff --quiet HEAD origin/main || die "local main differs from origin/main"
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
tty=/dev/tty
wants_push() {
	local question=$1 answer
	case ${usage_push-} in
	true) return 0 ;;
	false) return 1 ;;
	esac
	{ : <"$tty"; } 2>/dev/null || return 1
	read -r -p "$question [y/N] " answer <"$tty"
	[[ "$answer" = [yY]* ]]
}

push() {
	git push origin main "$1"
}

not_pushed() {
	echo "release: $1 is not on origin; publish it with: mise run release --push" >&2
}

# The latest release, unchanged since but not on origin: push it, as asked
push_unpushed() {
	local tag=$1
	if on_origin "$tag"; then
		echo "release: nothing to release: main is $tag, on origin" >&2
		return 0
	fi
	echo "release: nothing changed since $tag, which is not on origin" >&2
	[[ ${usage_dry_run:-false} = true ]] && return 0
	if wants_push "Push main and $tag to origin?"; then
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
			echo "git mv $old $new"
			continue
		fi
		git mv "$old" "$new"
	done < <(pending_migrations)
}

main() {
	local tag version latest pushing=false
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

	echo "Release: $tag" >&2
	if [[ ${usage_dry_run:-false} = true ]]; then
		rename_migrations "$version"
		exit 0
	fi

	if wants_push "Push main and $tag to origin once it is tested?"; then
		pushing=true
	fi

	mise run check
	rename_migrations "$version"
	"$checker" --final
	mise run test

	if ! git diff --cached --quiet; then
		git commit -m "chore(release): prepare $tag"
	fi

	git tag -s "$tag" -m "Release $tag"
	if [[ $pushing = true ]]; then
		push "$tag"
	else
		not_pushed "$tag"
	fi
}

if [[ ${BASH_SOURCE[0]} = "$0" ]]; then
	main "$@"
fi
