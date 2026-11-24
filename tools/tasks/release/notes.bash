#!/usr/bin/env bash
# SPDX-License-Identifier: MIT
#MISE description="Generate structured release notes for a release tag"
#USAGE include file="$MISE_CONFIG_ROOT/tools/tasks/release/notes.usage.kdl"
# Bash 3.2+ only: Linux and macOS.
set -euo pipefail

ui_prefix=release:notes
# shellcheck source=tools/lib/ui.bash
source "$(dirname "${BASH_SOURCE[0]}")/../../lib/ui.bash"

root=${REPO_ROOT:-$(cd "$(dirname "$0")/../../.." && pwd)}
cd "$root"

tag=${usage_tag:?}
repo=${usage_repo:-kludgeworks/eu-legislation-viewer}
output=${usage_output:-}

# The release tags, oldest version first
release_tags() {
	git tag --list --sort=version:refname | grep -E '^v20[0-9]{2}\.(0[1-9]|1[0-2])\.[0-9]+$' || true
}

# The previous release is the one with the version before, not the latest tag that the commit
# contains: a release's tag may be on a commit that main does not contain (v2026.11.19's is).
# A tag that isn't made yet is the release of HEAD: the Release workflow writes its notes before
# it tags it.
if git rev-parse --quiet --verify "refs/tags/$tag" >/dev/null; then
	ref=$tag
	prev_tag=$(release_tags | grep -B1 -Fx "$tag" | head -n 1)
	[[ "$prev_tag" != "$tag" ]] || prev_tag=""
else
	ref=HEAD
	prev_tag=$(release_tags | tail -n 1)
fi
if [[ -n "$prev_tag" ]]; then
	commit_range="$prev_tag..$ref"
	compare_url="https://github.com/$repo/compare/$prev_tag...$tag"
else
	commit_range="$ref"
	compare_url="https://github.com/$repo/commits/$tag"
fi

features=()
fixes=()
performance=()
docs=()
maintenance=()
others=()

tab=$(printf '\t')

while IFS="$tab" read -r full_sha short_sha subject; do
	[[ -z "$full_sha" ]] && continue
	if [[ "$subject" =~ ^chore\(release\):[[:space:]]*prepare[[:space:]]+v ]]; then
		continue
	fi
	link="https://github.com/$repo/commit/$full_sha"
	entry="* $subject ([\`$short_sha\`]($link))"
	case "$subject" in
	feat* | Feat*)
		features+=("$entry")
		;;
	fix* | Fix*)
		fixes+=("$entry")
		;;
	perf* | Perf*)
		performance+=("$entry")
		;;
	docs* | Docs*)
		docs+=("$entry")
		;;
	chore* | ci* | build* | test* | refactor*)
		maintenance+=("$entry")
		;;
	*)
		others+=("$entry")
		;;
	esac
done < <(git log "$commit_range" --no-merges --format="%H%x09%h%x09%s")

content=""

append_section() {
	local title=$1
	shift
	if (($# > 0)); then
		content+="$title"$'\n\n'
		for item in "$@"; do
			content+="$item"$'\n'
		done
		content+=$'\n'
	fi
}

content+="## What's Changed"$'\n\n'

append_section "### 🚀 Features" "${features[@]+"${features[@]}"}"
append_section "### 🐛 Bug Fixes" "${fixes[@]+"${fixes[@]}"}"
append_section "### ⚡ Performance Improvements" "${performance[@]+"${performance[@]}"}"
append_section "### 📚 Documentation" "${docs[@]+"${docs[@]}"}"
append_section "### 🛠️ Maintenance & Infrastructure" "${maintenance[@]+"${maintenance[@]}"}"
append_section "### Other Changes" "${others[@]+"${others[@]}"}"

content+="**Full Changelog**: $compare_url"

if [[ -n "$output" ]]; then
	mkdir -p "$(dirname "$output")"
	printf '%s\n' "$content" >"$output"
	info "Wrote release notes" path "$output"
else
	printf '%s\n' "$content"
fi
