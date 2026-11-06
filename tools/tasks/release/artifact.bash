#!/usr/bin/env bash
# SPDX-License-Identifier: MIT
#MISE description="Build the CalVer-stamped viewer JAR and its SHA-256 checksum"
#USAGE include file="$MISE_CONFIG_ROOT/tools/tasks/release/artifact.usage.kdl"
# Bash 3.2+ only: Linux and macOS.
set -euo pipefail

# sha256sum where there is one (Linux), else shasum, which every macOS has
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
