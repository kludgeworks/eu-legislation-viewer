#!/usr/bin/env bats
# SPDX-License-Identifier: MIT

load test_helper
bats_require_minimum_version 1.5.0

setup() {
	make_repo
}

notes() {
	run env REPO_ROOT="$repo" "$@" "$project/tools/tasks/release/notes.bash"
}

git_commit() {
	local message=$1
	write "$message" "$message"
	git -C "$repo" add .
	git -C "$repo" commit --quiet -m "$message"
}

@test "requires a tag" {
	notes

	[ "$status" -ne 0 ]
}

@test "categorizes commits since the previous release tag" {
	git_commit "chore: initial commit"
	git -C "$repo" tag v2026.11.0

	git_commit "feat(search): add full text search"
	git_commit "fix(viewer): handle missing metadata"
	git_commit "perf(cache): speed up document loading"
	git_commit "docs(readme): add docker run instructions"
	git_commit "chore(deps): update dependencies"
	git_commit "refactor(api): clean up response types"
	git_commit "ci(workflow): cache bazel artifacts"
	git_commit "test(e2e): add end-to-end coverage"
	git_commit "unconventional commit subject"
	git_commit "chore(release): prepare v2026.11.1"
	git -C "$repo" tag v2026.11.1

	notes usage_tag=v2026.11.1

	[ "$status" -eq 0 ]
	[[ "$output" = *"### 🚀 Features"* ]] || false
	[[ "$output" = *"* feat(search): add full text search"* ]] || false
	[[ "$output" = *"### 🐛 Bug Fixes"* ]] || false
	[[ "$output" = *"* fix(viewer): handle missing metadata"* ]] || false
	[[ "$output" = *"### ⚡ Performance Improvements"* ]] || false
	[[ "$output" = *"* perf(cache): speed up document loading"* ]] || false
	[[ "$output" = *"### 📚 Documentation"* ]] || false
	[[ "$output" = *"* docs(readme): add docker run instructions"* ]] || false
	[[ "$output" = *"### 🛠️ Maintenance & Infrastructure"* ]] || false
	[[ "$output" = *"* chore(deps): update dependencies"* ]] || false
	[[ "$output" = *"* refactor(api): clean up response types"* ]] || false
	[[ "$output" = *"* ci(workflow): cache bazel artifacts"* ]] || false
	[[ "$output" = *"* test(e2e): add end-to-end coverage"* ]] || false
	[[ "$output" = *"### Other Changes"* ]] || false
	[[ "$output" = *"* unconventional commit subject"* ]] || false

	# The release prep commit must be excluded
	[[ "$output" != *"chore(release): prepare v2026.11.1"* ]] || false

	# Initial commit before v2026.11.0 must not be included
	[[ "$output" != *"chore: initial commit"* ]] || false

	# Full Changelog compare link
	[[ "$output" = *"**Full Changelog**: https://github.com/kludgeworks/eu-legislation-viewer/compare/v2026.11.0...v2026.11.1"* ]] || false

	# Getting started, container and jar instructions
	[[ "$output" = *"docker pull ghcr.io/kludgeworks/eu-legislation-viewer:2026.11.1"* ]] || false
	[[ "$output" = *"java -jar kludgeworks-viewer-2026.11.1.jar"* ]] || false
	[[ "$output" = *"gh attestation verify kludgeworks-viewer-2026.11.1.jar --repo kludgeworks/eu-legislation-viewer"* ]] || false
}

@test "generates notes for the first tag without a previous tag" {
	git_commit "feat: initial feature"
	git_commit "fix: initial fix"
	git -C "$repo" tag v2026.11.0

	notes usage_tag=v2026.11.0

	[ "$status" -eq 0 ]
	[[ "$output" = *"### 🚀 Features"* ]] || false
	[[ "$output" = *"* feat: initial feature"* ]] || false
	[[ "$output" = *"### 🐛 Bug Fixes"* ]] || false
	[[ "$output" = *"* fix: initial fix"* ]] || false
	[[ "$output" = *"**Full Changelog**: https://github.com/kludgeworks/eu-legislation-viewer/commits/v2026.11.0"* ]] || false
}

@test "supports a custom repository name" {
	git_commit "feat: custom repo feature"
	git -C "$repo" tag v2026.11.0

	notes usage_tag=v2026.11.0 usage_repo=custom-org/custom-viewer

	[ "$status" -eq 0 ]
	[[ "$output" = *"https://github.com/custom-org/custom-viewer/commits/v2026.11.0"* ]] || false
	[[ "$output" = *"docker pull ghcr.io/custom-org/custom-viewer:2026.11.0"* ]] || false
	[[ "$output" = *"gh attestation verify kludgeworks-viewer-2026.11.0.jar --repo custom-org/custom-viewer"* ]] || false
}

@test "writes notes to the output file when specified" {
	git_commit "feat: output to file"
	git -C "$repo" tag v2026.11.0

	notes usage_tag=v2026.11.0 usage_output="$repo/dist/release-notes.md"

	[ "$status" -eq 0 ]
	[ -f "$repo/dist/release-notes.md" ]
	grep -Fq "feat: output to file" "$repo/dist/release-notes.md"
}
