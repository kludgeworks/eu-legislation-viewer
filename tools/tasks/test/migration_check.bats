#!/usr/bin/env bats
# SPDX-License-Identifier: MIT

load test_helper

setup() {
	make_repo
	write "viewer/src/main/resources/db/migration/catalogue/V2026_11_0_001__catalogue.sql" "create table act ();\n"
	commit
}

@test "allows migrations before the first release" {
	run env REPO_ROOT="$repo" "$project/tools/tasks/migration/check.bash"
	[ "$status" -eq 0 ]
}

@test "rejects a released migration whose contents change" {
	git -C "$repo" tag v2026.11.0
	write "viewer/src/main/resources/db/migration/catalogue/V2026_11_0_001__catalogue.sql" "create table changed ();\n"

	run env REPO_ROOT="$repo" "$project/tools/tasks/migration/check.bash"
	[ "$status" -ne 0 ]
	[[ "$output" = *"differs from released tag v2026.11.0"* ]]
}

@test "allows a new migration after a release" {
	git -C "$repo" tag v2026.11.0
	write "viewer/src/main/resources/db/migration/catalogue/V8__new.sql" "alter table act add column title text;\n"

	run env REPO_ROOT="$repo" "$project/tools/tasks/migration/check.bash"
	[ "$status" -eq 0 ]
}

@test "requires final CalVer names when requested" {
	write "viewer/src/main/resources/db/migration/catalogue/V8__new.sql" "alter table act add column title text;\n"

	run env REPO_ROOT="$repo" "$project/tools/tasks/migration/check.bash" --final
	[ "$status" -ne 0 ]
	[[ "$output" = *"non-final migration names"* ]]
}

@test "rejects duplicate versions in one module" {
	write "viewer/src/main/resources/db/migration/catalogue/V2026_11_0_001__again.sql" "alter table act add column title text;\n"

	run env REPO_ROOT="$repo" "$project/tools/tasks/migration/check.bash" --final
	[ "$status" -ne 0 ]
	[[ "$output" = *"duplicate migration versions"* ]]
}
