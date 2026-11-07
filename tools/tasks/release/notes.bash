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
version=${tag#v}
output=${usage_output:-}

# A tag that isn't made yet is the release of HEAD: mise run release shows its notes before it tags.
if git rev-parse --quiet --verify "refs/tags/$tag" >/dev/null; then
	ref=$tag
	prev_tag=$(git describe --tags --abbrev=0 "$tag^" 2>/dev/null || true)
else
	ref=HEAD
	prev_tag=$(git describe --tags --abbrev=0 HEAD 2>/dev/null || true)
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

content+="**Full Changelog**: $compare_url"$'\n\n'
content+="---"$'\n\n'
content+="## Getting Started"$'\n\n'
content+="### 🐳 Container Image"$'\n'
content+="Published on [GitHub Packages](https://github.com/$repo/pkgs/container/eu-legislation-viewer) (\`linux/amd64\`, \`linux/arm64\`):"$'\n\n'
content+='```bash'$'\n'
content+="docker pull ghcr.io/$repo:$version"$'\n'
content+="docker run -p 8080:8080 ghcr.io/$repo:$version"$'\n'
content+='```'$'\n\n'
content+="### ☕ Standalone JAR"$'\n'
content+="Requires Java 25:"$'\n\n'
content+='```bash'$'\n'
content+="java -jar kludgeworks-viewer-$version.jar"$'\n'
content+='```'$'\n\n'
content+="### 🔒 Verification"$'\n'
content+="Verify SLSA build provenance attestations:"$'\n\n'
content+='```bash'$'\n'
content+="gh attestation verify kludgeworks-viewer-$version.jar --repo $repo"$'\n'
content+="gh attestation verify oci://ghcr.io/$repo:$version --repo $repo"$'\n'
content+='```'

if [[ -n "$output" ]]; then
	mkdir -p "$(dirname "$output")"
	printf '%s\n' "$content" >"$output"
	info "Wrote release notes" path "$output"
else
	printf '%s\n' "$content"
fi
