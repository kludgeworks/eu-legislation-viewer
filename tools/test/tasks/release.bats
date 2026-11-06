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
	[[ "$output" = *"Would rename from=viewer/src/main/resources/db/migration/catalogue/NEXT_001__catalogue_title.sql to=V2026_11_0_001__catalogue_title.sql"* ]] || false
	[ -f "$repo/viewer/src/main/resources/db/migration/catalogue/NEXT_001__catalogue_title.sql" ]
}

@test "pushes with --push and not with --no-push, without asking" {
	ui_tty="$BATS_TEST_TMPDIR/no-terminal"
	usage_push=true wants_push v2026.11.0
	usage_push=false run ! wants_push v2026.11.0
}

@test "asks whether to push when neither --push nor --no-push is given" {
	ui_tty="$BATS_TEST_TMPDIR/answer"
	printf 'y\n' >"$ui_tty"
	wants_push v2026.11.0
	printf '\n' >"$ui_tty"
	run ! wants_push v2026.11.0
}

@test "does not push when there is no terminal to ask on" {
	ui_tty="$BATS_TEST_TMPDIR/no-terminal"
	run ! wants_push v2026.11.0
}

@test "sees nothing changed since a release until main changes" {
	git -C "$repo" tag v2026.11.0
	unchanged_since v2026.11.0
	write README.md "changed\n"
	commit
	run ! unchanged_since v2026.11.0
}

@test "knows a release is on origin only when the tag and main are both pushed" {
	make_origin
	git -C "$repo" tag v2026.11.0
	run ! on_origin v2026.11.0

	git -C "$repo" push --quiet origin v2026.11.0
	on_origin v2026.11.0

	write README.md "changed\n"
	commit
	git -C "$repo" tag -f v2026.11.0 >/dev/null
	git -C "$repo" push --quiet --force origin v2026.11.0
	run ! on_origin v2026.11.0
}

@test "makes no release when nothing changed since one on origin" {
	make_origin
	git -C "$repo" tag v2026.11.0
	git -C "$repo" push --quiet origin v2026.11.0

	run main

	[ "$status" -eq 0 ]
	[[ "$output" = *"Nothing to release: nothing changed since the latest release, which is published version=v2026.11.0"* ]] || false
	[ -z "$(git -C "$repo" tag --list 'v2026.11.1')" ]
}

@test "pushes the release a run before left local, with --push" {
	make_origin
	write README.md "prepared\n"
	commit
	git -C "$repo" tag v2026.11.0

	usage_push=true run main

	[ "$status" -eq 0 ]
	[ "$(git -C "$origin" rev-parse main)" = "$(git -C "$repo" rev-parse main)" ]
	[ "$(git -C "$origin" rev-parse v2026.11.0)" = "$(git -C "$repo" rev-parse v2026.11.0)" ]
}

@test "leaves the release a run before left local, with --no-push" {
	make_origin
	write README.md "prepared\n"
	commit
	git -C "$repo" tag v2026.11.0

	usage_push=false run main

	[ "$status" -eq 0 ]
	[[ "$output" = *"Kept local, not published version=v2026.11.0 publish=mise run release --push"* ]] || false
	[ -z "$(git -C "$origin" tag --list v2026.11.0)" ]
}

@test "only reports an unpushed release in a dry run" {
	make_origin
	git -C "$repo" tag v2026.11.0

	usage_dry_run=true usage_push=true run main

	[ "$status" -eq 0 ]
	[[ "$output" = *"Nothing changed since the latest release, but it isn't published: origin doesn't have it version=v2026.11.0"* ]] || false
	[ -z "$(git -C "$origin" tag --list v2026.11.0)" ]
}

@test "does not release a main that differs from origin's" {
	make_origin
	git -C "$repo" tag v2026.11.0
	git -C "$repo" push --quiet origin v2026.11.0
	write README.md "changed\n"
	commit

	run main

	[ "$status" -eq 1 ]
	[[ "$output" = *"main differs from origin/main: pull or push it first"* ]] || false
}

# A release of the fixture's pending migrations, with the check and tests stubbed.
prepare_release() {
	make_origin
	fake_signing
	stub mise
	checker=true
	RELEASE_MONTH=2026.11
}

@test "tags a release and leaves it local, with --no-push" {
	prepare_release

	usage_push=false run main

	[ "$status" -eq 0 ]
	[ "$(git -C "$repo" log -1 --format=%s v2026.11.0)" = "chore(release): prepare v2026.11.0" ]
	[ -f "$repo/viewer/src/main/resources/db/migration/ingest/V2026_11_0_001__ingest_job.sql" ]
	[[ "$output" = *"Kept local, not published version=v2026.11.0 publish=mise run release --push"* ]] || false
	[ -z "$(git -C "$origin" tag --list v2026.11.0)" ]
}

@test "tags a release and pushes it, with --push" {
	prepare_release

	usage_push=true run main

	[ "$status" -eq 0 ]
	[ "$(git -C "$origin" rev-parse v2026.11.0)" = "$(git -C "$repo" rev-parse v2026.11.0)" ]
	[ "$(git -C "$origin" rev-parse main)" = "$(git -C "$repo" rev-parse main)" ]
}

@test "pushes a release when asked to" {
	prepare_release
	ui_tty="$BATS_TEST_TMPDIR/answer"
	printf 'y\n' >"$ui_tty"

	run main

	[ "$status" -eq 0 ]
	[ "$(git -C "$origin" rev-parse v2026.11.0)" = "$(git -C "$repo" rev-parse v2026.11.0)" ]
}

@test "writes plain messages, with their values as key=value, where there is no terminal" {
	run info "Next release" version v2026.11.0 migrations 2

	[ "$output" = "release: Next release version=v2026.11.0 migrations=2" ]
}

@test "links to the Release workflow's runs of a GitHub origin" {
	git -C "$repo" remote add origin git@github.com:kludgeworks/eu-legislation-viewer.git
	[ "$(workflow_runs)" = https://github.com/kludgeworks/eu-legislation-viewer/actions/workflows/release.yml ]

	git -C "$repo" remote set-url origin https://github.com/kludgeworks/eu-legislation-viewer
	[ "$(workflow_runs)" = https://github.com/kludgeworks/eu-legislation-viewer/actions/workflows/release.yml ]

	git -C "$repo" remote set-url origin /srv/git/viewer.git
	[ -z "$(workflow_runs)" ]
}
