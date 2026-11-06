#!/usr/bin/env bash
# SPDX-License-Identifier: MIT
#MISE description="Build the viewer's container image from the release JAR"
#USAGE arg "<version>" help="The release's version: 2026.11.0" env="VERSION"
#USAGE flag "--image <image>" help="The image's name" env="IMAGE" default="ghcr.io/kludgeworks/eu-legislation-viewer"
#USAGE flag "--tag <tag>" help="The image's tag, the version if not given" env="TAG"
#USAGE flag "--platform <platform>" help="The platform to build for: linux/arm64" env="PLATFORM"
#USAGE flag "--publish" help="Push the image, keeping its build cache in the registry" env="PUBLISH"
#
# `mise run release:image 2026.11.0` builds the image into the local Docker from
# dist/kludgeworks-viewer-2026.11.0.jar (release:artifact). Paketo builds from the JAR, not from
# source: it has no Bazel buildpack, and the JAR is Spring Boot's own (BOOT-INF, JarLauncher), which
# its Spring Boot buildpack lays out in layers. The OCI source label links the image's GHCR package
# to this repository.
set -euo pipefail

root=${REPO_ROOT:-$(cd "$(dirname "$0")/../../.." && pwd)}
cd "$root"
version=${usage_version:?}
image=${usage_image:?}
artifact="dist/kludgeworks-viewer-$version.jar"
[[ -f "$artifact" ]] || {
	echo "release:image: $artifact is missing: run mise run release:artifact $version first" >&2
	exit 1
}

args=(
	--path "$artifact"
	--builder paketobuildpacks/builder-noble-java-tiny
	--env BP_JVM_VERSION=25
	--env BP_OCI_SOURCE=https://github.com/kludgeworks/eu.legislation.cli
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
