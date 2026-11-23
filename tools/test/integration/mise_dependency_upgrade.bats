#!/usr/bin/env bats
# SPDX-License-Identifier: MIT
# Network integration test. Run from a configured checkout with:
# mise exec -- bats tools/test/integration/mise_dependency_upgrade.bats

@test "a real mise upgrade preserves the config comments and installs from the new lock in a cold tool directory" {
	local project taplo_dir
	project=$(cd "$BATS_TEST_DIRNAME/../../.." && pwd)
	taplo_dir=$(dirname "$(mise which taplo)")
	export PATH="$taplo_dir:$PATH"
	cd "$BATS_TEST_TMPDIR"
	export MISE_TRUSTED_CONFIG_PATHS="$PWD" MISE_DATA_DIR="$PWD/data" MISE_CACHE_DIR="$PWD/cache"
	export MISE_GLOBAL_CONFIG_FILE="$PWD/config/global.toml"
	mkdir repo
	cd repo
	cat >mise.toml <<'TOML'
[settings]
lockfile = true

[tools]
# Pack builds the application image.
pack = "0.40.10"
TOML
	mise install pack
	git init -q
	git add mise.toml mise.lock
	git -c user.name=Fixture -c user.email=fixture@example.invalid -c commit.gpgSign=false commit -qm fixture
	local changes='[["mise","pack","0.40.10","0.40.11"]]'
	python3 -B "$project/tools/build/upgrade_dependencies.py" apply "$changes"
	grep -Fxq '# Pack builds the application image.' mise.toml
	grep -Fq '0.40.11' mise.lock
	git diff --binary >"$BATS_TEST_TMPDIR/mise.patch"
	git restore mise.toml mise.lock
	python3 -B "$project/tools/build/verify_dependency_patch.py" "$BATS_TEST_TMPDIR/mise.patch" "$changes"
	export MISE_DATA_DIR="$BATS_TEST_TMPDIR/cold-data" MISE_CACHE_DIR="$BATS_TEST_TMPDIR/cold-cache"
	mise install --locked pack
	[[ $(mise exec -- pack version) == 0.40.11* ]] || false

	# A read-only job must not be able to introduce a hook into the publisher's checkout.
	printf '\n[hooks]\npostinstall = "echo unplanned"\n' >>mise.toml
	git diff HEAD --binary >"$BATS_TEST_TMPDIR/extra-hook.patch"
	git restore --source=HEAD --staged --worktree mise.toml mise.lock
	run python3 -B "$project/tools/build/verify_dependency_patch.py" "$BATS_TEST_TMPDIR/extra-hook.patch" "$changes"
	[ "$status" -ne 0 ]
	[[ "$output" == *"Unexpected changes in mise.toml"* ]] || false
	grep -Fq '0.40.10' mise.toml
}
