#!/usr/bin/env bats
# SPDX-License-Identifier: MIT

load test_helper
bats_require_minimum_version 1.5.0

setup() {
	make_repo
	write "viewer/src/main/resources/db/migration/catalogue/V2026_10_0_001__catalogue.sql" "create table act ();\n"
	write "viewer/src/main/resources/db/migration/catalogue/NEXT_001__catalogue_title.sql" "alter table act add column title text;\n"
	write "viewer/src/main/resources/db/migration/ingest/NEXT_001__ingest_job.sql" "create table ingest_job ();\n"
	commit
	load_release
}

@test "calculates the first release of a month" {
	RELEASE_MONTH=2026.11
	[ "$(next_tag)" = v2026.11.0 ]
}

@test "increments the release within a month" {
	git -C "$repo" tag v2026.11.0
	git -C "$repo" tag v2026.11.1
	RELEASE_MONTH=2026.11
	[ "$(next_tag)" = v2026.11.2 ]
}

@test "resets the counter in a later month" {
	git -C "$repo" tag v2026.10.7
	RELEASE_MONTH=2026.11
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
	[[ "$output" = *"NEXT_001__catalogue_title.sql viewer/src/main/resources/db/migration/catalogue/V2026_11_0_001__catalogue_title.sql"* ]] || false
	[ -f "$repo/viewer/src/main/resources/db/migration/catalogue/NEXT_001__catalogue_title.sql" ]
}
