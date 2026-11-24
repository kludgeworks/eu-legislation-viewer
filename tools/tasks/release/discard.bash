#!/usr/bin/env bash
# SPDX-License-Identifier: MIT
#MISE description="Delete the images of a release that the Release workflow did not finish"
#MISE quiet=true
#USAGE include file="$MISE_CONFIG_ROOT/tools/tasks/release/discard.usage.kdl"
# Bash 3.2+ only: that includes the Bash shipped with supported macOS versions.
set -euo pipefail

ui_prefix=release:discard
# shellcheck source=tools/lib/ui.bash
source "$(dirname "${BASH_SOURCE[0]}")/../../lib/ui.bash"

# Whether origin has the release's tag: then the release is made, and its images are its own
tagged() {
	local repo=$1 tag=$2
	[[ -n "$(gh api "repos/$repo/git/matching-refs/tags/$tag" --jq ".[] | select(.ref == \"refs/tags/$tag\") | .ref")" ]]
}

# The ids of the package's versions that have one of the release's tags: the image, and the image
# of each architecture
versions_of() {
	local package=$1 version=$2
	gh api --paginate "$package/versions" |
		jq -r --arg version "$version" '
			.[] | select(.metadata.container.tags
				| any(. == $version or . == ($version + "-linux-amd64") or . == ($version + "-linux-arm64")))
			| .id'
}

main() {
	local version=${usage_version:?} repo=${usage_repo:?} image=${usage_image:?}
	local tag="v$version" owner name package id deleted=0
	if tagged "$repo" "$tag"; then
		info "Kept the images: the release is tagged, so they are its own" version "$tag"
		return 0
	fi
	[[ "$image" =~ ^ghcr\.io/([^/]+)/(.+)$ ]] || die "not an image of GitHub's container registry: $image"
	owner=${BASH_REMATCH[1]}
	name=${BASH_REMATCH[2]}
	package="/orgs/$owner/packages/container/${name//\//%2F}"
	while IFS= read -r id; do
		[[ -n "$id" ]] || continue
		gh api -X DELETE "$package/versions/$id" >/dev/null ||
			die "could not delete the image: the repository needs the Admin role in the package's Manage Actions access" \
				package "https://github.com/orgs/$owner/packages/container/package/$name"
		deleted=$((deleted + 1))
	done < <(versions_of "$package" "$version")
	info "Deleted the images of a release that was not made" version "$tag" images "$deleted"
}

if [[ ${BASH_SOURCE[0]} = "$0" ]]; then
	main "$@"
fi
