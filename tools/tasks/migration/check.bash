#!/usr/bin/env bash
# SPDX-License-Identifier: MIT
#MISE description="Check that released Flyway migrations remain immutable"
#
# `mise run migration:check` compares every migration in the latest reachable CalVer tag with
# the working tree. `--final` additionally requires every migration to have a final CalVer name.
# Bash 3.2+ only: that includes the Bash shipped with supported macOS versions.
set -euo pipefail

root=${REPO_ROOT:-$(cd "$(dirname "$0")/../../.." && pwd)}
cd "$root"
migrations=viewer/src/main/resources/db/migration

die() {
	echo "migration:check: $*" >&2
	exit 1
}

latest_tag() {
	git tag --list --sort=version:refname | grep -E '^v20[0-9]{2}\.(0[1-9]|1[0-2])\.[0-9]+$' |
		tail -n 1 || true
}

main() {
	local final=${1:-} tag invalid duplicates
	[[ -z "$final" || "$final" = --final ]] || die "expected no arguments or --final"
	tag=$(latest_tag)
	if [[ -z "$tag" ]]; then
		echo "migration:check: no CalVer release tag yet" >&2
	else
		while IFS= read -r path; do
			[[ -n "$path" ]] || continue
			if ! git diff --quiet "$tag" -- "$path"; then
				die "$path differs from released tag $tag"
			fi
		done < <(git ls-tree -r --name-only "$tag" -- "$migrations")
		echo "migration:check: migrations from $tag are unchanged" >&2
	fi

	if [[ "$final" = --final ]]; then
		invalid=$(rg --files "$migrations" -g '*.sql' |
			rg -v '/V20[0-9]{2}_(0[1-9]|1[0-2])_[0-9]+_[0-9]{3}__[^/]+\.sql$' || true)
		if [[ -n "$invalid" ]]; then
			printf 'migration:check: non-final migration names:\n%s\n' "$invalid" >&2
			exit 1
		fi

		duplicates=$(rg --files "$migrations" -g '*.sql' |
			sed -E 's#^(.*)/([^/]+)__.*$#\1/\2#' | sort | uniq -d)
		if [[ -n "$duplicates" ]]; then
			printf 'migration:check: duplicate migration versions:\n%s\n' "$duplicates" >&2
			exit 1
		fi
		echo "migration:check: every migration has a unique final CalVer name" >&2
	fi
}

if [[ ${BASH_SOURCE[0]} = "$0" ]]; then
	main "$@"
fi
