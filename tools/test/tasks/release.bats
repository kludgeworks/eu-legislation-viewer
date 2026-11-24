#!/usr/bin/env bats
# SPDX-License-Identifier: MIT

load test_helper
bats_require_minimum_version 1.5.0

setup() {
	make_repo
	write "deploy/k8s/base/viewer.yaml" $'image: ghcr.io/kludgeworks/eu-legislation-viewer:2026.10.0\n'
	commit
	make_origin
	load_release
	# origin is a local repository here, as GitHub's kludgeworks/viewer
	github_repo() {
		echo kludgeworks/viewer
	}
	RELEASE_POLL=0
	github
}

# gh as GitHub answers it: the dispatched runs on main are those in $BATS_TEST_TMPDIR/runs, one id
# per line, and a dispatch adds run 200. A run that has not ended is the one in
# $BATS_TEST_TMPDIR/running, if there is one. $run_fails makes the watched run fail. It keeps each
# call.
github() {
	printf '100\n' >"$BATS_TEST_TMPDIR/runs"
	mkdir -p "$BATS_TEST_TMPDIR/bin"
	cat >"$BATS_TEST_TMPDIR/bin/gh" <<GH
#!/usr/bin/env bash
printf '%s\n' "\$*" >>"$BATS_TEST_TMPDIR/gh.calls"
case "\$1 \$2" in
"run list") [[ "\$*" = *databaseId,status* ]] && { cat "$BATS_TEST_TMPDIR/running" 2>/dev/null; exit 0; }
	tac "$BATS_TEST_TMPDIR/runs" 2>/dev/null || tail -r "$BATS_TEST_TMPDIR/runs" ;;
"workflow run") printf '200\n' >>"$BATS_TEST_TMPDIR/runs" ;;
"run watch") [[ -z "\${run_fails:-}" ]] ;;
*) echo "unexpected: gh \$*" >&2; exit 1 ;;
esac
GH
	chmod +x "$BATS_TEST_TMPDIR/bin/gh"
	PATH="$BATS_TEST_TMPDIR/bin:$PATH"
}

@test "starts the Release workflow on main and follows its run" {
	run main

	[ "$status" -eq 0 ]
	grep -qx "workflow run release.yml --repo kludgeworks/viewer --ref main" "$BATS_TEST_TMPDIR/gh.calls"
	[[ "$output" = *"Started the Release workflow run=https://github.com/kludgeworks/viewer/actions/runs/200"* ]] || false
	grep -qx "run watch 200 --repo kludgeworks/viewer --exit-status" "$BATS_TEST_TMPDIR/gh.calls"
}

@test "fails when the release's run fails" {
	run_fails=true run main

	[ "$status" -ne 0 ]
}

@test "does not follow the run with --no-watch" {
	usage_watch=false run main

	[ "$status" -eq 0 ]
	run ! grep -q "run watch" "$BATS_TEST_TMPDIR/gh.calls"
}

@test "says so when it cannot find the run it started" {
	stub_dispatch_without_run

	run main

	[ "$status" -ne 0 ]
	[[ "$output" = *"started the Release workflow, but could not find its run"* ]] || false
}

stub_dispatch_without_run() {
	sed -i.bak 's/^"workflow run").*/"workflow run") ;;/' "$BATS_TEST_TMPDIR/bin/gh"
}

@test "shows what the workflow would release from origin's main in a dry run, starting nothing" {
	write README.md "pushed\n"
	commit
	git -C "$repo" push --quiet origin main
	write LOCAL.md "not pushed\n"
	git -C "$repo" add LOCAL.md
	git -C "$repo" commit --quiet -m "local only"
	write DIRTY.md "not committed\n"

	run env REPO_ROOT="$repo" RELEASE_MONTH=2026.11 usage_dry_run=true PATH="$PATH" \
		bash -c "source '$project/tools/tasks/release.bash'; main"

	[ "$status" -eq 0 ]
	[[ "$output" = *"Next release version=v2026.11.0"* ]] || false
	[[ "$output" = *"## What's Changed"* ]] || false
	[[ "$output" != *"local only"* ]] || false
	[ ! -f "$BATS_TEST_TMPDIR/gh.calls" ]
	[ "$(git -C "$repo" worktree list | grep -c .)" = 1 ]
	[ -f "$repo/DIRTY.md" ]
}

@test "warns of commits on main that origin doesn't have, and releases origin's main" {
	write README.md "not pushed\n"
	commit

	run main

	[ "$status" -eq 0 ]
	[[ "$output" = *"main here has commits that origin's doesn't: they won't be in the release commits=1"* ]] || false
	grep -qx "workflow run release.yml --repo kludgeworks/viewer --ref main" "$BATS_TEST_TMPDIR/gh.calls"
}

@test "releases origin's main whatever is checked out here, without a warning" {
	write DIRTY.md "not committed\n"
	git -C "$repo" checkout --quiet -b other

	run main

	[ "$status" -eq 0 ]
	[[ "$output" != *"won't be in the release"* ]] || false
	grep -qx "workflow run release.yml --repo kludgeworks/viewer --ref main" "$BATS_TEST_TMPDIR/gh.calls"
}

@test "finds origin's GitHub repository, and refuses an origin that is not on GitHub" {
	load_release
	git -C "$repo" remote set-url origin git@github.com:kludgeworks/eu-legislation-viewer.git
	[ "$(github_repo)" = kludgeworks/eu-legislation-viewer ]
	git -C "$repo" remote set-url origin https://github.com/kludgeworks/eu-legislation-viewer
	[ "$(github_repo)" = kludgeworks/eu-legislation-viewer ]
	git -C "$repo" remote set-url origin /srv/git/viewer.git
	run github_repo
	[ "$status" -ne 0 ]
	[[ "$output" = *"origin is not on GitHub: /srv/git/viewer.git"* ]] || false
}

@test "starts no release while one is running, and follows that one when asked to" {
	printf '150\n' >"$BATS_TEST_TMPDIR/running"
	ui_tty="$BATS_TEST_TMPDIR/answer"
	printf 'y\n' >"$ui_tty"

	run main

	[ "$status" -eq 0 ]
	[[ "$output" = *"A release is running already, so none is started run=https://github.com/kludgeworks/viewer/actions/runs/150"* ]] || false
	run ! grep -q "workflow run" "$BATS_TEST_TMPDIR/gh.calls"
	grep -qx "run watch 150 --repo kludgeworks/viewer --exit-status" "$BATS_TEST_TMPDIR/gh.calls"
}

@test "fails when the running release that it follows fails" {
	printf '150\n' >"$BATS_TEST_TMPDIR/running"
	ui_tty="$BATS_TEST_TMPDIR/answer"
	printf 'y\n' >"$ui_tty"

	run_fails=true run main

	[ "$status" -ne 0 ]
}

@test "does not follow the running release when told no" {
	printf '150\n' >"$BATS_TEST_TMPDIR/running"
	ui_tty="$BATS_TEST_TMPDIR/answer"
	printf '\n' >"$ui_tty"

	run main

	[ "$status" -eq 0 ]
	run ! grep -q "workflow run\|run watch" "$BATS_TEST_TMPDIR/gh.calls"
}

@test "does not ask about the running release with --no-watch, or without a terminal" {
	printf '150\n' >"$BATS_TEST_TMPDIR/running"

	ui_tty="$BATS_TEST_TMPDIR/no-terminal" run main
	[ "$status" -eq 0 ]
	usage_watch=false ui_tty="$BATS_TEST_TMPDIR/answer" run main
	[ "$status" -eq 0 ]

	run ! grep -q "workflow run\|run watch" "$BATS_TEST_TMPDIR/gh.calls"
}
