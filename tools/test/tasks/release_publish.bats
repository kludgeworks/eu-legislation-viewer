#!/usr/bin/env bats
# SPDX-License-Identifier: MIT

load test_helper
bats_require_minimum_version 1.5.0

setup() {
	make_repo
	write "viewer/src/main/resources/db/migration/ingest/NEXT_001__ingest_job.sql" $'create table ingest_job ();\n'
	write "deploy/k8s/base/viewer.yaml" $'image: ghcr.io/kludgeworks/eu-legislation-viewer:2026.10.0\n'
	commit
	base=$(git -C "$repo" rev-parse HEAD)
	# A release, prepared as release:prepare does: a rename and a change, staged
	git -C "$repo" mv viewer/src/main/resources/db/migration/ingest/NEXT_001__ingest_job.sql \
		viewer/src/main/resources/db/migration/ingest/V2026_11_0_001__ingest_job.sql
	write "deploy/k8s/base/viewer.yaml" $'image: ghcr.io/kludgeworks/eu-legislation-viewer:2026.11.0\n'
	git -C "$repo" add deploy/k8s/base/viewer.yaml
	tree=$(git -C "$repo" write-tree)
	load_publish
	usage_tag=v2026.11.0 usage_repo=kludgeworks/viewer usage_base=$base usage_tree=$tree
	github
}

# gh as GitHub's API answers it: the commit's oid, the commit's tree ($committed_tree, else the
# tested tree), and the tag object's sha. It keeps what each call was sent in $BATS_TEST_TMPDIR.
# $commit_fails makes the commit fail, as when main moved.
github() {
	mkdir -p "$BATS_TEST_TMPDIR/bin"
	cat >"$BATS_TEST_TMPDIR/bin/gh" <<GH
#!/usr/bin/env bash
printf '%s\n' "\$*" >>"$BATS_TEST_TMPDIR/gh.calls"
case "\$*" in
"api graphql --input -"*)
	cat >"$BATS_TEST_TMPDIR/commit.json"
	[[ -z "\${commit_fails:-}" ]] || { echo "Expected branch to point to $base" >&2; exit 1; }
	echo 1111111111111111111111111111111111111111 ;;
"api repos/kludgeworks/viewer/git/commits/1111111111111111111111111111111111111111"*)
	echo "\${committed_tree:-$tree}" ;;
"api repos/kludgeworks/viewer/git/tags"*)
	echo 2222222222222222222222222222222222222222 ;;
"api repos/kludgeworks/viewer/git/refs"*) ;;
*) echo "unexpected: gh \$*" >&2; exit 1 ;;
esac
GH
	chmod +x "$BATS_TEST_TMPDIR/bin/gh"
	PATH="$BATS_TEST_TMPDIR/bin:$PATH"
}

decoded() {
	jq -r --arg path "$1" '.variables.input.fileChanges.additions[] | select(.path == $path) | .contents' \
		"$BATS_TEST_TMPDIR/commit.json" | base64 --decode
}

@test "commits the staged release on main, only if main is still at the base" {
	run main

	[ "$status" -eq 0 ]
	input=$(jq -c '.variables.input | {branch, message, expectedHeadOid}' "$BATS_TEST_TMPDIR/commit.json")
	[ "$input" = "{\"branch\":{\"repositoryNameWithOwner\":\"kludgeworks/viewer\",\"branchName\":\"main\"},\"message\":{\"headline\":\"chore(release): prepare v2026.11.0\"},\"expectedHeadOid\":\"$base\"}" ]
	[[ "$output" = *"Committed, signed commit=1111111111111111111111111111111111111111"* ]] || false
}

@test "commits a renamed migration as its old path deleted and its new path added, with its content" {
	main >/dev/null

	[ "$(jq -r '.variables.input.fileChanges.deletions[].path' "$BATS_TEST_TMPDIR/commit.json")" = \
		viewer/src/main/resources/db/migration/ingest/NEXT_001__ingest_job.sql ]
	[ "$(jq -r '.variables.input.fileChanges.additions[].path' "$BATS_TEST_TMPDIR/commit.json" | sort)" = "deploy/k8s/base/viewer.yaml
viewer/src/main/resources/db/migration/ingest/V2026_11_0_001__ingest_job.sql" ]
	[ "$(decoded viewer/src/main/resources/db/migration/ingest/V2026_11_0_001__ingest_job.sql)" = "create table ingest_job ();" ]
	[ "$(decoded deploy/k8s/base/viewer.yaml)" = "image: ghcr.io/kludgeworks/eu-legislation-viewer:2026.11.0" ]
}

@test "tags the release's commit, with an annotated tag, after it is committed" {
	main >/dev/null

	[ "$(grep -c . "$BATS_TEST_TMPDIR/gh.calls")" = 4 ]
	grep -qx "api repos/kludgeworks/viewer/git/tags -f tag=v2026.11.0 -f message=Release v2026.11.0 -f object=1111111111111111111111111111111111111111 -f type=commit --jq .sha" "$BATS_TEST_TMPDIR/gh.calls"
	grep -qx "api repos/kludgeworks/viewer/git/refs -f ref=refs/tags/v2026.11.0 -f sha=2222222222222222222222222222222222222222" "$BATS_TEST_TMPDIR/gh.calls"
}

@test "does not publish a tree that is not the one the workflow tested" {
	write README.md "not tested\n"
	git -C "$repo" add README.md

	run main

	[ "$status" -ne 0 ]
	[[ "$output" = *"the prepared tree is not the one the workflow tested"* ]] || false
	[ ! -f "$BATS_TEST_TMPDIR/gh.calls" ]
}

@test "does not tag when main moved since the release began" {
	commit_fails=true run main

	[ "$status" -ne 0 ]
	[[ "$output" = *"could not commit the release to main: main may have moved since the release began"* ]] || false
	run ! grep -q "git/tags\|git/refs" "$BATS_TEST_TMPDIR/gh.calls"
}

@test "does not tag a commit whose tree is not the one the workflow tested" {
	committed_tree=3333333333333333333333333333333333333333 run main

	[ "$status" -ne 0 ]
	[[ "$output" = *"the commit on main is not the tree the workflow tested"* ]] || false
	run ! grep -q "git/tags\|git/refs" "$BATS_TEST_TMPDIR/gh.calls"
}
