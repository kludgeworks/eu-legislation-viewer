#!/usr/bin/env bash
# SPDX-License-Identifier: MIT
#MISE description="Build the CalVer-stamped viewer JAR and its SHA-256 checksum"
#USAGE arg "<version>" help="The release's version: 2026.11.0" env="VERSION"
#
# `mise run release:artifact 2026.11.0` writes dist/kludgeworks-viewer-2026.11.0.jar, Spring Boot's
# executable jar with the version embedded, and its .sha256 beside it. Linux and macOS (Bash 3.2+):
# sha256sum where there is one, else shasum, which every macOS has.
set -euo pipefail

checksum() {
	if command -v sha256sum >/dev/null; then
		sha256sum "$@"
	else
		shasum -a 256 "$@"
	fi
}

root=${REPO_ROOT:-$(cd "$(dirname "$0")/../../.." && pwd)}
cd "$root"
version=${usage_version:?}
artifact="dist/kludgeworks-viewer-$version.jar"

bazel build --embed_label="$version" //viewer:jar
mkdir -p dist
# install, not cp: Bazel's outputs are read-only, so cp couldn't replace an earlier build's copy
install -m 0644 bazel-bin/viewer/src/main/java/dev/groknull/kludgeworks/eu/legislation/viewer/viewer-app.jar "$artifact"
checksum "$artifact" >"$artifact.sha256"
