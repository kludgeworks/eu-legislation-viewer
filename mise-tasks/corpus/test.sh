#!/usr/bin/env bash
# SPDX-License-Identifier: MIT
#MISE description="Run the regression test on one language's mirror (see corpus:fetch)"
#USAGE arg "<mirror>" help="One language's mirror, e.g. ../formex-corpus/eng"
#
# Bazel caches the result: the converter by its own dependency graph, the mirror by the hash of its
# manifest (every complete manifestation with Cellar's lastModified), passed as a test env value,
# which is part of the test's cache key. Unchanged both, the test isn't run again.
set -euo pipefail

manifest=${usage_mirror:?}/manifest.tsv
if [ ! -s "$manifest" ]; then
	echo "corpus:test: no corpus at ${usage_mirror}: run mise run corpus:fetch first" >&2
	echo "  (corpus:fetch writes DIR/{language}; give corpus:test that language's folder)" >&2
	exit 1
fi
mirror=$(cd "$usage_mirror" && pwd)

bazel test //:corpus --test_env=CORPUS="$mirror" \
	--test_env=CORPUS_MANIFEST="$(shasum -a 256 "$manifest" | cut -c1-64)"
