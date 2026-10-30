#!/usr/bin/env bash
# SPDX-License-Identifier: MIT
#MISE description="Run the regression test on one language's mirror (see corpus:fetch)"
# EUR-Lex's 24 languages, by their EU authority codes: every language Cellar has Formex in
#USAGE flag "--language <code>" default="ENG" help="An EU authority language code" { choices "BUL" "CES" "DAN" "DEU" "ELL" "ENG" "EST" "FIN" "FRA" "GLE" "HRV" "HUN" "ITA" "LAV" "LIT" "MLT" "NLD" "POL" "POR" "RON" "SLK" "SLV" "SPA" "SWE" }
#
# The mirror is formex-corpus/{language} at the repository root, made by corpus:fetch. Bazel caches
# the result: the converter by its own dependency graph, the mirror by the hash of its manifest
# (every complete manifestation with Cellar's lastModified), passed as a test env value, which is
# part of the test's cache key. Unchanged both, the test isn't run again.
set -euo pipefail

root=$(cd "$(dirname "$0")/../../.." && pwd)
language=$(echo "${usage_language:?}" | tr '[:upper:]' '[:lower:]')
mirror=$root/formex-corpus/$language
if [ ! -s "$mirror/manifest.tsv" ]; then
	echo "corpus:test: no corpus at $mirror: run mise run corpus:fetch --language ${usage_language} first" >&2
	exit 1
fi

bazel test //:corpus --test_env=CORPUS="$mirror" \
	--test_env=CORPUS_MANIFEST="$(shasum -a 256 "$mirror/manifest.tsv" | cut -c1-64)"
