#!/usr/bin/env bash
# SPDX-License-Identifier: MIT
set -euo pipefail

PMD_CLI="$1"

# In Bazel test, sources are in the runfiles tree or workspace
ROOT="${BUILD_WORKSPACE_DIRECTORY:-}"
if [ -z "$ROOT" ]; then
	# Test environment: locate workspace root from runfiles
	ROOT="$PWD"
	while [ "$ROOT" != "/" ] && [ ! -f "$ROOT/WORKSPACE" ] && [ ! -f "$ROOT/MODULE.bazel" ]; do
		ROOT="$(dirname "$ROOT")"
	done
fi

TMP_FILE="$(mktemp)"
trap 'rm -f "$TMP_FILE"' EXIT

find "$ROOT/viewer/src/main/java" "$ROOT/tools/build/src/main/java" -name "*.java" >"$TMP_FILE"

"$PMD_CLI" cpd \
	--minimum-tokens 100 \
	--file-list "$TMP_FILE" \
	--language java \
	--ignore-annotations \
	--fail-on-violation \
	--no-fail-on-error
