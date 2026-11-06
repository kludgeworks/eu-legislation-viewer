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
	git -C "$repo" config commit.gpgSign false
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

# A bare repository as the repo's origin, with main pushed to it. The repo prunes tags on fetch,
# as some people's git does, so a script must not lose a tag that only it has.
make_origin() {
	origin="$BATS_TEST_TMPDIR/origin.git"
	git init --quiet --bare "$origin"
	git -C "$repo" config fetch.prune true
	git -C "$repo" config fetch.pruneTags true
	git -C "$repo" remote add origin "$origin"
	git -C "$repo" push --quiet origin main
	git -C "$repo" fetch --quiet origin
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

# git tag -s signs with a stub of gpg: a signed tag, without a key.
fake_signing() {
	stub fake-gpg 'echo "[GNUPG:] SIG_CREATED " >&2; printf -- "-----BEGIN PGP SIGNATURE-----\nfake\n-----END PGP SIGNATURE-----\n"'
	git -C "$repo" config gpg.format openpgp
	git -C "$repo" config gpg.program "$BATS_TEST_TMPDIR/bin/fake-gpg"
	git -C "$repo" config user.signingkey tests@example.invalid
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
