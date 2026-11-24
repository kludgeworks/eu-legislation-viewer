#!/usr/bin/env bash
# SPDX-License-Identifier: MIT
#MISE description="Commit a prepared release to main as the GitHub App, signed, and tag it"
#MISE quiet=true
#USAGE include file="$MISE_CONFIG_ROOT/tools/tasks/release/publish.usage.kdl"
# Bash 3.2+ only: that includes the Bash shipped with supported macOS versions.
set -euo pipefail

ui_prefix=release:publish
# shellcheck source=tools/lib/ui.bash
source "$(dirname "${BASH_SOURCE[0]}")/../../lib/ui.bash"

root=${REPO_ROOT:-$(cd "$(dirname "$0")/../../.." && pwd)}
cd "$root"

# The staged changes from the base, as createCommitOnBranch's fileChanges: each added or changed
# file with its staged content in Base64, and each deleted path. A rename is both.
file_changes() {
	local base=$1 status path
	git diff --cached -z --name-status --no-renames "$base" |
		while IFS= read -r -d '' status && IFS= read -r -d '' path; do
			if [[ $status = D ]]; then
				jq -cn --arg path "$path" '{deletions: [{path: $path}]}'
			else
				git show ":$path" | base64 | tr -d '\n' |
					jq -cR --arg path "$path" '{additions: [{path: $path, contents: .}]}'
			fi
		done |
		jq -cs '{additions: (map(.additions // []) | add // []), deletions: (map(.deletions // []) | add // [])}'
}

# Commits the staged changes on main as the token's App, which GitHub signs. It fails, changing
# nothing, if main is no longer at the base: the release was prepared and tested on the base.
commit_on_main() {
	local repo=$1 base=$2 message=$3
	jq -n \
		--arg repo "$repo" \
		--arg base "$base" \
		--arg message "$message" \
		--argjson changes "$(file_changes "$base")" \
		'{
			query: "mutation($input: CreateCommitOnBranchInput!) { createCommitOnBranch(input: $input) { commit { oid } } }",
			variables: {input: {
				branch: {repositoryNameWithOwner: $repo, branchName: "main"},
				message: {headline: $message},
				expectedHeadOid: $base,
				fileChanges: $changes
			}}
		}' |
		gh api graphql --input - --jq '.data.createCommitOnBranch.commit.oid'
}

main() {
	local tag=${usage_tag:?} repo=${usage_repo:?} base=${usage_base:?} tree=${usage_tree:?}
	local prepared commit committed object
	prepared=$(git write-tree)
	[[ "$prepared" = "$tree" ]] ||
		die "the prepared tree is not the one the workflow tested" prepared "$prepared" tested "$tree"

	commit=$(commit_on_main "$repo" "$base" "chore(release): prepare $tag") ||
		die "could not commit the release to main: main may have moved since the release began" base "$base"
	[[ -n "$commit" ]] || die "GitHub returned no commit for the release"
	committed=$(gh api "repos/$repo/git/commits/$commit" --jq '.tree.sha')
	[[ "$committed" = "$tree" ]] ||
		die "the commit on main is not the tree the workflow tested" commit "$commit" tested "$tree"
	info "Committed, signed" commit "$commit" message "chore(release): prepare $tag"

	object=$(gh api "repos/$repo/git/tags" \
		-f tag="$tag" -f message="Release $tag" -f object="$commit" -f type=commit --jq '.sha')
	gh api "repos/$repo/git/refs" -f ref="refs/tags/$tag" -f sha="$object" >/dev/null
	info "Tagged" version "$tag" commit "$commit"
}

if [[ ${BASH_SOURCE[0]} = "$0" ]]; then
	main "$@"
fi
