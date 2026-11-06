#!/usr/bin/env bats
# SPDX-License-Identifier: MIT

load test_helper

jar=bazel-bin/viewer/src/main/java/dev/groknull/kludgeworks/eu/legislation/viewer/viewer-app.jar

setup() {
	make_repo
	# bazel build leaves Spring Boot's jar where Bazel puts it, read-only as Bazel's outputs are
	stub bazel "mkdir -p $(dirname "$jar") && rm -f $jar && printf 'the jar' >$jar && chmod 0444 $jar"
}

artifact() {
	run env REPO_ROOT="$repo" "$@" "$project/tools/tasks/release/artifact.bash"
}

@test "builds the JAR with the version embedded" {
	artifact usage_version=2026.11.0

	[ "$status" -eq 0 ]
	[ "$(args_of bazel)" = "$(printf '%s\n' build --embed_label=2026.11.0 //viewer:jar)" ]
}

@test "writes the JAR and its checksum to dist" {
	artifact usage_version=2026.11.0

	[ "$status" -eq 0 ]
	[ "$(cat "$repo/dist/kludgeworks-viewer-2026.11.0.jar")" = "the jar" ]
	(cd "$repo" && shasum -a 256 -c dist/kludgeworks-viewer-2026.11.0.jar.sha256)
}

@test "replaces an earlier build of the same version" {
	artifact usage_version=2026.11.0
	artifact usage_version=2026.11.0

	[ "$status" -eq 0 ]
}

@test "requires a version" {
	artifact

	[ "$status" -ne 0 ]
	[ ! -e "$BATS_TEST_TMPDIR/bazel.args" ]
}
