#!/usr/bin/env bash
# SPDX-License-Identifier: MIT

project=$(cd "$BATS_TEST_DIRNAME/../../.." && pwd)

make_repo() {
	repo="$BATS_TEST_TMPDIR/repo"
	mkdir -p "$repo"
	git init --quiet "$repo"
	git -C "$repo" checkout --quiet -b main
	git -C "$repo" config user.email tests@example.invalid
	git -C "$repo" config user.name "Task tests"
	git -C "$repo" config tag.gpgSign false
}

write() {
	local path=$1
	shift
	mkdir -p "$(dirname "$repo/$path")"
	printf '%s' "$*" >"$repo/$path"
}

commit() {
	git -C "$repo" add .
	git -C "$repo" commit --quiet -m fixture
}

load_check() {
	REPO_ROOT="$repo" source "$project/tools/tasks/migration/check.bash"
}

load_release() {
	REPO_ROOT="$repo" source "$project/tools/tasks/release.bash"
}
