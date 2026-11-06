#!/usr/bin/env bash
# SPDX-License-Identifier: MIT

# An assertion with [[ ]] ends in || false: macOS's Bash (3.2) doesn't stop a test on a [[ ]] that
# fails, unless it is the test's last command (bats-core's "Gotchas").

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

# A command on PATH that records its arguments, one per line, in $BATS_TEST_TMPDIR/<name>.args,
# then runs the rest of its arguments here as its body.
stub() {
	local name=$1
	shift
	mkdir -p "$BATS_TEST_TMPDIR/bin"
	cat >"$BATS_TEST_TMPDIR/bin/$name" <<EOF
#!/usr/bin/env bash
printf '%s\n' "\$@" >"$BATS_TEST_TMPDIR/$name.args"
$*
EOF
	chmod +x "$BATS_TEST_TMPDIR/bin/$name"
	PATH="$BATS_TEST_TMPDIR/bin:$PATH"
}

# The arguments the stub {name} was called with, one per line.
args_of() {
	cat "$BATS_TEST_TMPDIR/$1.args"
}

load_check() {
	# shellcheck source=/dev/null # the task itself, checked on its own
	REPO_ROOT="$repo" source "$project/tools/tasks/release/db-migrations.bash"
}

load_release() {
	# shellcheck source=/dev/null # the task itself, checked on its own
	REPO_ROOT="$repo" source "$project/tools/tasks/release.bash"
}
