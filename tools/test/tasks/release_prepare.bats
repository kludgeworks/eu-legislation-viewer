#!/usr/bin/env bats
# SPDX-License-Identifier: MIT

load test_helper
bats_require_minimum_version 1.5.0

setup() {
	make_repo
	write "viewer/src/main/resources/db/migration/catalogue/V2026_10_0_001__catalogue.sql" "create table act ();\n"
	write "viewer/src/main/resources/db/migration/catalogue/NEXT_001__catalogue_title.sql" "alter table act add column title text;\n"
	write "viewer/src/main/resources/db/migration/ingest/NEXT_001__ingest_job.sql" "create table ingest_job ();\n"
	write "deploy/k8s/base/viewer.yaml" $'containers:\n  - name: viewer\n    image: ghcr.io/kludgeworks/eu-legislation-viewer:2026.10.0\n'
	commit
	load_prepare
	RELEASE_MONTH=2026.11
}

@test "calculates the first release of a month" {
	[ "$(next_tag)" = v2026.11.0 ]
}

@test "increments the release within a month" {
	git -C "$repo" tag v2026.11.0
	git -C "$repo" tag v2026.11.1
	[ "$(next_tag)" = v2026.11.2 ]
}

@test "resets the counter in a later month" {
	git -C "$repo" tag v2026.10.7
	[ "$(next_tag)" = v2026.11.0 ]
}

@test "does not permit a release version to move backwards" {
	run ! is_later_than v2026.11.1 v2026.11.1
	run ! is_later_than v2026.11.0 v2026.11.1
	is_later_than v2026.12.0 v2026.11.9
}

@test "finds the pending migrations" {
	[ "$(pending_migrations)" = "viewer/src/main/resources/db/migration/catalogue/NEXT_001__catalogue_title.sql
viewer/src/main/resources/db/migration/ingest/NEXT_001__ingest_job.sql" ]
}

@test "names pending migrations with the release's version, keeping their numbers" {
	rename_migrations 2026.11.0

	[ -f "$repo/viewer/src/main/resources/db/migration/catalogue/V2026_10_0_001__catalogue.sql" ]
	[ -f "$repo/viewer/src/main/resources/db/migration/catalogue/V2026_11_0_001__catalogue_title.sql" ]
	[ -f "$repo/viewer/src/main/resources/db/migration/ingest/V2026_11_0_001__ingest_job.sql" ]
	[ -z "$(pending_migrations)" ]
	[ "$(git -C "$repo" diff --cached --name-status -M | awk '{print $1}' | sort -u)" = R100 ]
}

@test "shows the renames without making them in a dry run" {
	usage_dry_run=true run rename_migrations 2026.11.0

	[ "$status" -eq 0 ]
	[[ "$output" = *"Would rename from=viewer/src/main/resources/db/migration/catalogue/NEXT_001__catalogue_title.sql to=V2026_11_0_001__catalogue_title.sql"* ]] || false
	[ -f "$repo/viewer/src/main/resources/db/migration/catalogue/NEXT_001__catalogue_title.sql" ]
}

@test "pins the deployment's image to the release's version, staged" {
	pin_image 2026.11.0

	[ "$(cat "$repo/deploy/k8s/base/viewer.yaml")" = 'containers:
  - name: viewer
    image: ghcr.io/kludgeworks/eu-legislation-viewer:2026.11.0' ]
	[ "$(git -C "$repo" diff --cached --name-only)" = deploy/k8s/base/viewer.yaml ]
}

@test "shows the image's pin without making it in a dry run" {
	usage_dry_run=true run pin_image 2026.11.0

	[ "$status" -eq 0 ]
	[[ "$output" = *"Would pin the image version=2026.11.0 in=deploy/k8s/base/viewer.yaml"* ]] || false
	grep -q 'viewer:2026.10.0$' "$repo/deploy/k8s/base/viewer.yaml"
}

@test "does not release without one image of the viewer to pin" {
	write "deploy/k8s/base/viewer.yaml" $'containers:\n  - name: viewer\n    image: ghcr.io/kludgeworks/other:1\n'

	run pin_image 2026.11.0

	[ "$status" -ne 0 ]
	[[ "$output" = *"has no image ghcr.io/kludgeworks/eu-legislation-viewer to pin, or more than one"* ]] || false
}

@test "sees nothing changed since a release until main changes" {
	git -C "$repo" tag v2026.11.0
	unchanged_since v2026.11.0
	write README.md "changed\n"
	commit
	run ! unchanged_since v2026.11.0
}

@test "prepares the release, staged, and writes its tag, version and tree for the workflow" {
	usage_output="$BATS_TEST_TMPDIR/output" run main

	[ "$status" -eq 0 ]
	[ -f "$repo/viewer/src/main/resources/db/migration/ingest/V2026_11_0_001__ingest_job.sql" ]
	grep -q 'viewer:2026.11.0$' "$repo/deploy/k8s/base/viewer.yaml"
	[ "$(cat "$BATS_TEST_TMPDIR/output")" = "tag=v2026.11.0
version=2026.11.0
tree=$(git -C "$repo" write-tree)" ]
	[ "$(git -C "$repo" rev-list --count HEAD)" = 1 ]
}

@test "prepares the same tree each time from the same commit" {
	usage_output="$BATS_TEST_TMPDIR/first" main >/dev/null
	git -C "$repo" reset --quiet --hard
	git -C "$repo" clean --quiet -fd
	usage_output="$BATS_TEST_TMPDIR/second" main >/dev/null

	[ "$(cat "$BATS_TEST_TMPDIR/first")" = "$(cat "$BATS_TEST_TMPDIR/second")" ]
}

@test "prepares nothing, and writes an empty tag, when nothing changed since the latest release" {
	git -C "$repo" tag v2026.11.0

	usage_output="$BATS_TEST_TMPDIR/output" run main

	[ "$status" -eq 0 ]
	[[ "$output" = *"Nothing to release: nothing changed since the latest release version=v2026.11.0"* ]] || false
	[ "$(cat "$BATS_TEST_TMPDIR/output")" = "tag=" ]
	[ -z "$(git -C "$repo" status --porcelain)" ]
}

@test "does not prepare a release on a working tree with changes" {
	write README.md "not committed\n"

	run main

	[ "$status" -ne 0 ]
	[[ "$output" = *"the working tree has changes"* ]] || false
}

@test "shows the next release's notes in a dry run, changing nothing" {
	usage_dry_run=true run main

	[ "$status" -eq 0 ]
	[[ "$output" = *"Next release version=v2026.11.0 migrations=2"* ]] || false
	[[ "$output" = *"## What's Changed"* ]] || false
	[[ "$output" = *"* fixture"* ]] || false
	[ -z "$(git -C "$repo" status --porcelain)" ]
}

@test "writes plain messages, with their values as key=value, where there is no terminal" {
	run info "Next release" version v2026.11.0 migrations 2

	[ "$output" = "release:prepare: Next release version=v2026.11.0 migrations=2" ]
}

@test "prints Markdown as it is where there is no terminal" {
	run markdown <<<"## What's Changed"

	[ "$output" = "## What's Changed" ]
}
