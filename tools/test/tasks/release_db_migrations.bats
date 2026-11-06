#!/usr/bin/env bats
# SPDX-License-Identifier: MIT

load test_helper
bats_require_minimum_version 1.5.0

migrations=viewer/src/main/resources/db/migration

setup() {
	make_repo
	write "$migrations/catalogue/V2026_11_0_001__catalogue.sql" "create table act ();\n"
	commit
	git -C "$repo" tag v2026.11.0
}

check() {
	run env REPO_ROOT="$repo" "$project/tools/tasks/release/db-migrations.bash" "$@"
}

# the migrations' paths below db/migration, sorted
names() {
	(cd "$repo/$migrations" && find . -type f | sed 's#^\./##' | LC_ALL=C sort)
}

@test "allows pending migrations before the first release" {
	git -C "$repo" tag -d v2026.11.0
	git -C "$repo" mv "$migrations/catalogue/V2026_11_0_001__catalogue.sql" "$migrations/catalogue/NEXT_001__catalogue.sql"

	check
	[ "$status" -eq 0 ]
}

@test "rejects a released migration whose contents change" {
	write "$migrations/catalogue/V2026_11_0_001__catalogue.sql" "create table changed ();\n"

	check
	[ "$status" -ne 0 ]
	[[ "$output" = *"differs from released tag v2026.11.0"* ]] || false
}

@test "rejects a released migration renamed" {
	git -C "$repo" mv "$migrations/catalogue/V2026_11_0_001__catalogue.sql" "$migrations/catalogue/NEXT_001__catalogue.sql"

	check --fix
	[ "$status" -ne 0 ]
	[[ "$output" = *"differs from released tag v2026.11.0"* ]] || false
}

@test "allows pending migrations numbered per module" {
	write "$migrations/catalogue/NEXT_001__catalogue_title.sql" "alter table act add column title text;\n"
	write "$migrations/ingest/NEXT_001__ingest_job.sql" "create table ingest_job ();\n"

	check
	[ "$status" -eq 0 ]
}

@test "allows a plain description in Spring Modulith's own migrations" {
	write "$migrations/__root/NEXT_001__event_publication.sql" "create table event_publication ();\n"

	check
	[ "$status" -eq 0 ]
}

@test "fails on a misnamed migration, naming the fix" {
	write "$migrations/catalogue/V8__title.sql" "alter table act add column title text;\n"

	check
	[ "$status" -ne 0 ]
	[[ "$output" = *"V8__title.sql -> NEXT_001__catalogue_title.sql"* ]] || false
	[ -f "$repo/$migrations/catalogue/V8__title.sql" ]
}

@test "fixes descriptions, padding, the prefix and the module's name" {
	write "$migrations/catalogue/next_1_Step-Count.SQL" "select 1;\n"
	write "$migrations/ingest/NEXT_2__Job  State.sql" "select 2;\n"
	write "$migrations/__root/V1__Event_Publication.sql" "select 3;\n"

	check --fix
	[ "$status" -eq 0 ]
	[ "$(names)" = "__root/NEXT_001__event_publication.sql
catalogue/NEXT_001__catalogue_step_count.sql
catalogue/V2026_11_0_001__catalogue.sql
ingest/NEXT_001__ingest_job_state.sql" ]
	check
	[ "$status" -eq 0 ]
}

@test "closes gaps in a module's numbers, in the order they run" {
	write "$migrations/catalogue/NEXT_003__catalogue_b.sql" "select 3;\n"
	write "$migrations/catalogue/NEXT_007__catalogue_c.sql" "select 7;\n"
	write "$migrations/content/NEXT_002__content_a.sql" "select 2;\n"

	check --fix
	[ "$status" -eq 0 ]
	[ "$(names)" = "catalogue/NEXT_001__catalogue_b.sql
catalogue/NEXT_002__catalogue_c.sql
catalogue/V2026_11_0_001__catalogue.sql
content/NEXT_001__content_a.sql" ]
}

@test "takes a name another gives up" {
	write "$migrations/catalogue/NEXT_002__catalogue_b.sql" "select 2;\n"
	write "$migrations/catalogue/NEXT_001__b.sql" "select 1;\n"

	check --fix
	[ "$status" -eq 0 ]
	[ "$(names)" = "catalogue/NEXT_001__catalogue_b.sql
catalogue/NEXT_002__catalogue_b.sql
catalogue/V2026_11_0_001__catalogue.sql" ]
}

@test "puts new migrations after the pending ones: numbered in order, an unnumbered one last" {
	write "$migrations/catalogue/NEXT_001__catalogue_a.sql" "select 1;\n"
	write "$migrations/catalogue/last.sql" "select 4;\n"
	write "$migrations/catalogue/V9__c.sql" "select 3;\n"
	write "$migrations/catalogue/V8__b.sql" "select 2;\n"

	check --fix
	[ "$status" -eq 0 ]
	[ "$(names)" = "catalogue/NEXT_001__catalogue_a.sql
catalogue/NEXT_002__catalogue_b.sql
catalogue/NEXT_003__catalogue_c.sql
catalogue/NEXT_004__catalogue_last.sql
catalogue/V2026_11_0_001__catalogue.sql" ]
}

@test "renames with git what git has" {
	write "$migrations/catalogue/V8__title.sql" "select 1;\n"
	commit

	check --fix
	[ "$status" -eq 0 ]
	[ "$(git -C "$repo" diff --cached --name-status -M | awk '{print $1, $3}')" = "R100 $migrations/catalogue/NEXT_001__catalogue_title.sql" ]
}

@test "fails, without renaming, on two pending migrations with one number in a module" {
	write "$migrations/catalogue/NEXT_001__catalogue_a.sql" "select 1;\n"
	write "$migrations/catalogue/NEXT_001__catalogue_b.sql" "select 2;\n"

	check --fix
	[ "$status" -ne 0 ]
	[[ "$output" = *"one number, so which runs first?"* ]] || false
	[[ "$output" = *"NEXT_001__catalogue_a.sql"* && "$output" = *"NEXT_001__catalogue_b.sql"* ]] || false
	[ -f "$repo/$migrations/catalogue/NEXT_001__catalogue_b.sql" ]
}

@test "fails on two unnumbered migrations in a module" {
	write "$migrations/catalogue/a.sql" "select 1;\n"
	write "$migrations/catalogue/b.sql" "select 2;\n"

	check --fix
	[ "$status" -ne 0 ]
	[[ "$output" = *"no numbers, so which runs first?"* ]] || false
}

@test "fails on what can't be named" {
	write "$migrations/catalogue/NEXT_001.sql" "select 1;\n"
	write "$migrations/catalogue/R__view.sql" "select 2;\n"
	write "$migrations/catalogue/notes.md" "notes\n"
	write "$migrations/loose.sql" "select 3;\n"
	write "$migrations/catalogue/deeper/NEXT_002__a.sql" "select 4;\n"
	write "$migrations/ingest/NEXT_001__$(printf 'a%.0s' $(seq 1 200)).sql" "select 5;\n"

	check --fix
	[ "$status" -ne 0 ]
	[[ "$output" = *"NEXT_001.sql: no description"* ]] || false
	[[ "$output" = *"R__view.sql: a repeatable migration"* ]] || false
	[[ "$output" = *"notes.md: not a migration (.sql)"* ]] || false
	[[ "$output" = *"loose.sql: not in a module's directory"* ]] || false
	[[ "$output" = *"deeper/NEXT_002__a.sql: not in a module's directory"* ]] || false
	[[ "$output" = *"a description over 200 characters"* ]] || false
}

@test "requires final CalVer names when requested" {
	write "$migrations/catalogue/NEXT_001__catalogue_title.sql" "alter table act add column title text;\n"

	check --final
	[ "$status" -ne 0 ]
	[[ "$output" = *"Not named as released migrations are"* ]] || false
}

@test "rejects one version twice in a module when final" {
	write "$migrations/catalogue/V2026_11_0_001__catalogue_again.sql" "select 1;\n"

	check --final
	[ "$status" -ne 0 ]
	[[ "$output" = *"Migration versions used twice"* ]] || false
}

@test "allows one version in two modules when final" {
	write "$migrations/ingest/V2026_11_0_001__ingest_job.sql" "create table ingest_job ();\n"

	check --final
	[ "$status" -eq 0 ]
}
