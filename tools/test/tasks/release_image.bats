#!/usr/bin/env bats
# SPDX-License-Identifier: MIT

load test_helper
bats_require_minimum_version 1.5.0

image=ghcr.io/kludgeworks/eu-legislation-viewer

setup() {
	make_repo
	write dist/kludgeworks-viewer-2026.11.0.jar "the jar"
	commit
	stub pack
}

build() {
	run env REPO_ROOT="$repo" usage_version=2026.11.0 usage_image="$image" "$@" \
		"$project/tools/tasks/release/image.bash"
}

# whether pack was given {argument}, or {flag} followed by {value}
given() {
	if [ $# -eq 1 ]; then
		args_of pack | grep -Fqx -- "$1"
	else
		args_of pack | awk -v flag="$1" -v value="$2" \
			'previous == flag && $0 == value { found = 1 } { previous = $0 } END { exit !found }'
	fi
}

@test "builds the image from the release JAR, tagged with the version" {
	build

	[ "$status" -eq 0 ]
	[ "$(args_of pack | head -n 2)" = "$(printf '%s\n' build "$image:2026.11.0")" ]
	given --path dist/kludgeworks-viewer-2026.11.0.jar
	given --builder paketobuildpacks/builder-noble-java-tiny
	given --env BP_JVM_VERSION=25
}

@test "labels the image with its source, version and revision" {
	build

	given --env BP_OCI_SOURCE=https://github.com/kludgeworks/eu.legislation.cli
	given --env BP_OCI_VERSION=2026.11.0
	given --env "BP_OCI_REVISION=$(git -C "$repo" rev-parse HEAD)"
}

@test "builds into the local Docker unless asked to publish" {
	build

	run ! given --publish
	run ! given --cache-image
	run ! given --platform
}

@test "publishes one platform's image, keeping its build cache in the registry" {
	build usage_tag=2026.11.0-linux-arm64 usage_platform=linux/arm64 usage_publish=true

	[ "$status" -eq 0 ]
	[ "$(args_of pack | sed -n 2p)" = "$image:2026.11.0-linux-arm64" ]
	given --platform linux/arm64
	given --publish
	given --cache-image "$image:build-cache-linux-arm64"
}

@test "refuses to build without the release JAR" {
	rm "$repo/dist/kludgeworks-viewer-2026.11.0.jar"

	build

	[ "$status" -ne 0 ]
	[[ "$output" = *"run mise run release:artifact 2026.11.0 first"* ]] || false
	[ ! -e "$BATS_TEST_TMPDIR/pack.args" ]
}
