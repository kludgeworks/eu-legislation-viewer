#!/usr/bin/env bats
# SPDX-License-Identifier: MIT

load test_helper

setup() {
	make_repo
	write "viewer/src/main/resources/db/migration/catalogue/V1__catalogue.sql" "create table act ();\n"
	write "viewer/src/main/resources/db/migration/catalogue/V2__title.sql" "alter table act add column title text;\n"
	write "viewer/src/main/resources/db/migration/ingest/V1__job.sql" "create table ingest_job ();\n"
	write "viewer/src/main/java/dev/groknull/kludgeworks/eu/legislation/viewer/catalogue/infra/BUILD.bazel" "migrations = [\n  \"//viewer/src/main/resources:db/migration/catalogue/V1__catalogue.sql\",\n  \"//viewer/src/main/resources:db/migration/catalogue/V2__title.sql\",\n]\n"
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
	! is_later_than v2026.11.1 v2026.11.1
	! is_later_than v2026.11.0 v2026.11.1
	is_later_than v2026.12.0 v2026.11.9
}

@test "finds only migrations added since the last release" {
	git -C "$repo" tag v2026.10.0
	write "viewer/src/main/resources/db/migration/catalogue/V3__state.sql" "alter table act add column state text;\n"

	[ "$(pending_migrations)" = "viewer/src/main/resources/db/migration/catalogue/V3__state.sql" ]
}

@test "renames pending migrations per module and updates Bazel labels" {
	rename_migrations 2026.11.0

	[ -f "$repo/viewer/src/main/resources/db/migration/catalogue/V2026_11_0_001__catalogue.sql" ]
	[ -f "$repo/viewer/src/main/resources/db/migration/catalogue/V2026_11_0_002__title.sql" ]
	[ -f "$repo/viewer/src/main/resources/db/migration/ingest/V2026_11_0_001__job.sql" ]
	grep -F "V2026_11_0_001__catalogue.sql" "$repo/viewer/src/main/java/dev/groknull/kludgeworks/eu/legislation/viewer/catalogue/infra/BUILD.bazel"
	grep -F "V2026_11_0_002__title.sql" "$repo/viewer/src/main/java/dev/groknull/kludgeworks/eu/legislation/viewer/catalogue/infra/BUILD.bazel"
}
