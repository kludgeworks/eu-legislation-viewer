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
# per line, and a dispatch adds run 200. $run_fails makes the watched run fail. It keeps each call.
github() {
	printf '100\n' >"$BATS_TEST_TMPDIR/runs"
	mkdir -p "$BATS_TEST_TMPDIR/bin"
	cat >"$BATS_TEST_TMPDIR/bin/gh" <<GH
#!/usr/bin/env bash
printf '%s\n' "\$*" >>"$BATS_TEST_TMPDIR/gh.calls"
case "\$1 \$2" in
"run list") tac "$BATS_TEST_TMPDIR/runs" 2>/dev/null || tail -r "$BATS_TEST_TMPDIR/runs" ;;
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

@test "shows the next release in a dry run, without starting the workflow" {
	run env REPO_ROOT="$repo" RELEASE_MONTH=2026.11 usage_dry_run=true PATH="$PATH" \
		bash -c "source '$project/tools/tasks/release.bash'; main"

	[ "$status" -eq 0 ]
	[[ "$output" = *"Next release version=v2026.11.0"* ]] || false
	run ! grep -q "workflow run" "$BATS_TEST_TMPDIR/gh.calls"
}

@test "does not release a main that differs from origin's" {
	write README.md "changed\n"
	commit

	run main

	[ "$status" -ne 0 ]
	[[ "$output" = *"main differs from origin/main: pull or push it first"* ]] || false
	[ ! -f "$BATS_TEST_TMPDIR/gh.calls" ]
}

@test "does not release with changes in the working tree" {
	write README.md "not committed\n"

	run main

	[ "$status" -ne 0 ]
	[[ "$output" = *"the working tree has changes"* ]] || false
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

@test "does not release from a branch other than main" {
	git -C "$repo" checkout --quiet -b other

	run main

	[ "$status" -ne 0 ]
	[[ "$output" = *"release from main, not other"* ]] || false
}
