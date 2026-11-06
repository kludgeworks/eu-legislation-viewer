#!/usr/bin/env bash
# SPDX-License-Identifier: MIT
#MISE description="Build the viewer's container image from the release JAR"
#USAGE include file="$MISE_CONFIG_ROOT/tools/tasks/release/image.usage.kdl"
set -euo pipefail

ui_prefix=release:image
# shellcheck source=tools/tasks/lib/ui.bash
source "$(dirname "${BASH_SOURCE[0]}")/../lib/ui.bash"

root=${REPO_ROOT:-$(cd "$(dirname "$0")/../../.." && pwd)}
cd "$root"
version=${usage_version:?}
image=${usage_image:?}
artifact="dist/kludgeworks-viewer-$version.jar"
[[ -f "$artifact" ]] || die "$artifact is missing: run mise run release:artifact $version first"

args=(
	--path "$artifact"
	--builder paketobuildpacks/builder-noble-java-tiny
	# Paketo's, as the builders pack trusts are, but not on its list: untrusted, pack saves the
	# lifecycle image out of Docker, which fails on Docker 29's containerd image store (CI's)
	--trust-builder
	--env BP_JVM_VERSION=25
	--env BP_OCI_SOURCE=https://github.com/kludgeworks/eu-legislation-viewer
	--env BP_OCI_VERSION="$version"
	--env BP_OCI_REVISION="$(git rev-parse HEAD)"
)
if [[ -n ${usage_platform:-} ]]; then
	args+=(--platform "$usage_platform")
fi
# CI runners start empty, so a published build keeps its cache in the registry, per platform
if [[ ${usage_publish:-false} = true ]]; then
	cache=${usage_platform:-local}
	args+=(--publish --cache-image "$image:build-cache-${cache//\//-}")
fi
pack build "$image:${usage_tag:-$version}" "${args[@]}"
