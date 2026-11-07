#!/usr/bin/env bash
# SPDX-License-Identifier: MIT
#MISE description="Check the Flyway migrations' names, and that released ones are unchanged"
#MISE quiet=true
#MISE sources=["viewer/src/main/resources/db/migration/**/*", "tools/tasks/release/db-migrations.bash", ".git/packed-refs", ".git/refs/tags/**"]
#MISE outputs={auto=true}
#USAGE include file="$MISE_CONFIG_ROOT/tools/tasks/release/db-migrations.usage.kdl"
# Bash 3.2+ only: that includes the Bash shipped with supported macOS versions.
set -euo pipefail

ui_prefix=release:db-migrations
# shellcheck source=tools/lib/ui.bash
source "$(dirname "${BASH_SOURCE[0]}")/../../lib/ui.bash"

root=${REPO_ROOT:-$(cd "$(dirname "$0")/../../.." && pwd)}
cd "$root"
migrations=viewer/src/main/resources/db/migration
root_module=__root # Spring Modulith's migrations, of no module
tab=$'\t'

latest_tag() {
	git tag --list --sort=version:refname | grep -E '^v20[0-9]{2}\.(0[1-9]|1[0-2])\.[0-9]+$' |
		tail -n 1 || true
}

# released migrations as the latest tag has them
check_released() {
	local tag=$1 path
	while IFS= read -r path; do
		[[ -n "$path" ]] || continue
		if ! git diff --quiet "$tag" -- "$path"; then
			die "A released migration changed: $path differs from released tag $tag" restore "git checkout $tag -- $path"
		fi
	done < <(git ls-tree -r --name-only "$tag" -- "$migrations")
	info "Released migrations are unchanged" version "$tag"
}

# The files hk passes (none: every file), and so the modules they are in: a module's pending
# migrations are numbered together, so it is checked and fixed whole
selection='' scope=''

selected() {
	[[ -z "$selection" ]] || grep -qxF -- "$1" <<<"$selection"
}

in_scope() {
	[[ -z "$selection" ]] || grep -qxF -- "$1" <<<"$scope"
}

# A file's problem, to report, if it is one of the files selected: the others in its module are
# read for their numbers alone, their problems their own commits'. In pending, whose problems it adds to.
problem() {
	if selected "$1"; then
		problems="$problems$1: $2\n"
	fi
}

# lower case words joined by _
description() {
	echo "$1" | tr '[:upper:]' '[:lower:]' | sed -E 's/[^a-z0-9]+/_/g; s/^_+//; s/_+$//'
}

# each part of a version zero-padded, so that sort orders them as numbers
version_key() {
	local part key=''
	for part in $(echo "$1" | tr '._' '  '); do
		key="$key$(printf '%09d' "$((10#$part))")."
	done
	echo "$key"
}

# the module's name, as a description's first word: none in Spring Modulith's own
module_word() {
	local module=${1%/*}
	module=${module##*/}
	[[ "$module" = "$root_module" ]] || echo "$module"
}

# a description that starts with the module's name
with_module() {
	local module
	module=$(module_word "$1")
	if [[ -z "$module" || "$2" = "$module" || "$2" = "$module"_* ]]; then
		echo "$2"
	else
		echo "${module}_$2"
	fi
}

# The pending migrations of the modules in scope, a line each: their order (0: NEXT_, 1: numbered
# otherwise, 2: unnumbered), its key, the module's directory, the description and the path. What
# can't be named, of the files selected, is reported, and ends the run.
pending() {
	local tag=$1 path relative name stem class key summary problems=''
	while IFS= read -r path; do
		if ! selected "$path" && ! in_scope "${path%/*}"; then
			continue
		fi
		relative=${path#"$migrations"/}
		if [[ "$relative" != */* || "$relative" == */*/* ]]; then
			problem "$path" "not in a module's directory ($migrations/<module>/)"
			continue
		fi
		if [[ -n "$tag" ]] && git cat-file -e "$tag:$path" 2>/dev/null; then
			continue
		fi
		name=${path##*/}
		if [[ ! "$name" =~ \.[Ss][Qq][Ll]$ ]]; then
			problem "$path" "not a migration (.sql)"
			continue
		fi
		stem=${name%.*}
		if [[ "$stem" =~ ^[Rr]__ ]]; then
			problem "$path" "a repeatable migration (R__): migrations here are versioned"
			continue
		elif [[ "$stem" =~ ^[Nn][Ee][Xx][Tt]_*([0-9]+)(_+(.*))?$ ]]; then
			class=0 key=$(version_key "${BASH_REMATCH[1]}") summary=${BASH_REMATCH[3]}
		elif [[ "$stem" =~ ^[Vv]?([0-9]+([._][0-9]+)*)(_+(.*))?$ ]]; then
			class=1 key=$(version_key "${BASH_REMATCH[1]}") summary=${BASH_REMATCH[4]}
		else
			class=2 key=- summary=$stem # read joins empty fields between tabs
		fi
		summary=$(description "$summary")
		if [[ -z "$summary" ]]; then
			problem "$path" "no description: name what it does (NEXT_<NNN>__<description>.sql)"
			continue
		fi
		summary=$(with_module "$path" "$summary")
		if [[ ${#summary} -gt 200 ]]; then
			problem "$path" "a description over 200 characters, more than Flyway keeps"
			continue
		fi
		printf '%s\t%s\t%s\t%s\t%s\n' "$class" "$key" "${path%/*}" "$summary" "$path"
	done < <(find "$migrations" -mindepth 1 \( -type f -o -type l \) | LC_ALL=C sort)
	if [[ -n "$problems" ]]; then
		log error "$(printf "These can't be named:\n%b" "$problems")"
		return 1
	fi
}

# The renames that give the pending migrations their names, a line each: from, to. In a module, two
# NEXT_ with one number, or two unnumbered, are reported, and end the run.
renames() {
	local lines=$1 class key module summary path number=0 previous='' clashes target
	clashes=$(echo "$lines" | awk -F "$tab" '
		$1 == 0 { k = $3 "\t" $2; paths[k] = paths[k] "\n  " $5; n[k]++ }
		$1 == 2 { unnumbered[$3] = unnumbered[$3] "\n  " $5; u[$3]++ }
		END {
			for (k in paths) if (n[k] > 1) printf "one number, so which runs first?%s\n", paths[k]
			for (m in unnumbered) if (u[m] > 1) printf "no numbers, so which runs first?%s\n", unnumbered[m]
		}')
	if [[ -n "$clashes" ]]; then
		log error "$(printf "Name these NEXT_<NNN>__<description>.sql in the order they run:\n%s" "$clashes")"
		return 1
	fi
	while IFS="$tab" read -r class key module summary path; do
		[[ -n "$path" ]] || continue
		if [[ "$module" != "$previous" ]]; then
			previous=$module
			number=0
		fi
		number=$((number + 1))
		target="$module/NEXT_$(printf '%03d' "$number")__$summary.sql"
		if [[ "$target" != "$path" ]]; then
			printf '%s\t%s\n' "$path" "$target"
		fi
	done < <(echo "$lines" | LC_ALL=C sort -t "$tab" -k3,3 -k1,1n -k2,2)
}

# a file renamed as git sees it, when git has it
move() {
	if git ls-files --error-unmatch -- "$1" >/dev/null 2>&1; then
		git mv -- "$1" "$2"
	else
		mv -- "$1" "$2"
	fi
}

# The renames, by way of names of their own: so that one may take a name another gives up.
apply() {
	local from to
	while IFS="$tab" read -r from to; do
		move "$from" "$to.renaming"
	done <<<"$1"
	while IFS="$tab" read -r from to; do
		move "$to.renaming" "$to"
		info "Renamed" from "$from" to "${to##*/}"
	done <<<"$1"
}

check_final() {
	local invalid duplicates
	invalid=$(find "$migrations" -mindepth 1 \( -type f -o -type l \) |
		grep -Ev "^$migrations/[^/]+/V20[0-9]{2}_(0[1-9]|1[0-2])_[0-9]+_[0-9]{3}__[a-z0-9]+(_[a-z0-9]+)*\.sql$" || true)
	if [[ -n "$invalid" ]]; then
		die "$(printf 'Not named as released migrations are:\n%s' "$invalid")"
	fi
	duplicates=$(find "$migrations" -type f -name '*.sql' | sed -E 's#^(.*)/([^/]+)__.*$#\1/\2#' | sort | uniq -d)
	if [[ -n "$duplicates" ]]; then
		die "$(printf 'Migration versions used twice:\n%s' "$duplicates")"
	fi
	info "Every migration has its released name, each version once"
}

main() {
	local tag lines planned
	[[ ${usage_fix:-false} != true || ${usage_final:-false} != true ]] || die "--fix and --final don't go together"
	[[ ${usage_final:-false} != true || -z "$selection" ]] || die "--final checks every migration: no files"
	tag=$(latest_tag)
	if [[ -z "$tag" ]]; then
		info "No release yet: no released migrations to compare"
	else
		check_released "$tag"
	fi

	if [[ ${usage_final:-false} = true ]]; then
		check_final
		return
	fi

	lines=$(pending "$tag") || exit 1
	planned=$(renames "$lines") || exit 1
	if [[ -z "$planned" ]]; then
		info "Every pending migration is named NEXT_<NNN>__<description>.sql"
	elif [[ ${usage_fix:-false} = true ]]; then
		apply "$planned"
	else
		die "$(
			echo "Misnamed: mise run release:db-migrations --fix renames them"
			while IFS="$tab" read -r from to; do
				echo "  $from -> ${to##*/}"
			done <<<"$planned"
		)"
	fi
}

if [[ ${BASH_SOURCE[0]} = "$0" ]]; then
	# flags and files as mise passes them, when the script is run itself (the release, CI, hk)
	for arg in "$@"; do
		case $arg in
		--fix) usage_fix=true ;;
		--final) usage_final=true ;;
		-*) die "unknown argument: $arg" ;;
		*)
			selection="$selection${selection:+$'\n'}$arg"
			scope="$scope${scope:+$'\n'}${arg%/*}"
			;;
		esac
	done
	main
fi
