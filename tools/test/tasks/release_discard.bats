#!/usr/bin/env bats
# SPDX-License-Identifier: MIT

load test_helper
bats_require_minimum_version 1.5.0

setup() {
	load_discard
	usage_version=2026.11.20 usage_repo=kludgeworks/viewer usage_image=ghcr.io/kludgeworks/eu-legislation-viewer
	github
}

package=/orgs/kludgeworks/packages/container/eu-legislation-viewer

# gh as GitHub's API answers it. origin has the tags in $tags (none by default). The package has
# the image of the release (1), of each of its architectures (2, 3), of an earlier release (4),
# and an untagged one (5). $delete_fails makes a deletion fail, as without the Admin role.
github() {
	mkdir -p "$BATS_TEST_TMPDIR/bin"
	cat >"$BATS_TEST_TMPDIR/bin/gh" <<GH
#!/usr/bin/env bash
printf '%s\n' "\$*" >>"$BATS_TEST_TMPDIR/gh.calls"
case "\$*" in
"api repos/kludgeworks/viewer/git/matching-refs/tags/"*)
	for tag in \${tags:-}; do [[ "\$*" = *"tags/\$tag --jq"* ]] && echo "refs/tags/\$tag"; done; true ;;
"api --paginate $package/versions")
	echo '[{"id":1,"metadata":{"container":{"tags":["2026.11.20"]}}},
		{"id":2,"metadata":{"container":{"tags":["2026.11.20-linux-amd64"]}}},
		{"id":3,"metadata":{"container":{"tags":["2026.11.20-linux-arm64"]}}},
		{"id":4,"metadata":{"container":{"tags":["2026.11.19","2026.11.19-linux-amd64"]}}},
		{"id":5,"metadata":{"container":{"tags":[]}}}]' ;;
"api -X DELETE $package/versions/"*)
	[[ -z "\${delete_fails:-}" ]] || { echo "HTTP 403" >&2; exit 1; } ;;
*) echo "unexpected: gh \$*" >&2; exit 1 ;;
esac
GH
	chmod +x "$BATS_TEST_TMPDIR/bin/gh"
	PATH="$BATS_TEST_TMPDIR/bin:$PATH"
}

deleted() {
	grep "^api -X DELETE" "$BATS_TEST_TMPDIR/gh.calls" | sed 's#.*/versions/##' | sort | tr '\n' ' '
}

@test "deletes the release's image and the image of each architecture, and nothing else" {
	run main

	[ "$status" -eq 0 ]
	[ "$(deleted)" = "1 2 3 " ]
	[[ "$output" = *"Deleted the images of a release that was not made version=v2026.11.20 images=3"* ]] || false
}

@test "keeps the images of a release that is tagged" {
	tags="v2026.11.20" run main

	[ "$status" -eq 0 ]
	[[ "$output" = *"Kept the images: the release is tagged, so they are its own version=v2026.11.20"* ]] || false
	[ -z "$(deleted)" ]
}

@test "deletes the images when only another release with a like name is tagged" {
	tags="v2026.11.2" run main

	[ "$status" -eq 0 ]
	[ "$(deleted)" = "1 2 3 " ]
}

@test "deletes nothing when the run published no image" {
	usage_version=2026.11.21 run main

	[ "$status" -eq 0 ]
	[[ "$output" = *"images=0"* ]] || false
	[ -z "$(deleted)" ]
}

@test "says which setting is missing when it may not delete the images" {
	delete_fails=true run main

	[ "$status" -ne 0 ]
	[[ "$output" = *"the repository needs the Admin role in the package's Manage Actions access"* ]] || false
}

@test "refuses an image that is not in GitHub's container registry" {
	usage_image=docker.io/kludgeworks/viewer run main

	[ "$status" -ne 0 ]
	[[ "$output" = *"not an image of GitHub's container registry: docker.io/kludgeworks/viewer"* ]] || false
}
