#!/usr/bin/env bats
# SPDX-License-Identifier: MIT

load test_helper

setup() {
	export GITHUB_REPOSITORY=example/repo
	export GITHUB_OUTPUT="$BATS_TEST_TMPDIR/output"
	cd "$project"
}

@test "passes open branches to the planner and publishes the matrix" {
	stub gh 'cat <<JSON
[[{"base":{"repo":{"full_name":"example/repo"}},"head":{"repo":{"full_name":"fork/repo"},"ref":"fork"}}],
 [{"base":{"repo":{"full_name":"example/repo"}},"head":{"repo":{"full_name":"example/repo"},"ref":"automation/example"}}]]
JSON'
	stub python3 'echo "{\"include\":[]}"'
	run bash tools/build/plan-dependency-prs.bash
	[ "$status" -eq 0 ]
	[ "$(cat "$GITHUB_OUTPUT")" = 'matrix={"include":[]}' ]
	[ "$(cat "$BATS_TEST_TMPDIR/python3.args")" = $'tools/build/upgrade_dependencies.py\nplan\n["automation/example"]' ]
}

@test "a GitHub inventory failure cannot allocate new PR slots" {
	stub gh 'exit 1'
	stub python3 'exit 0'
	run bash tools/build/plan-dependency-prs.bash
	[ "$status" -ne 0 ]
	[ ! -e "$BATS_TEST_TMPDIR/python3.args" ]
	[ ! -s "$GITHUB_OUTPUT" ]
}

@test "a failed planner cannot publish a partial matrix" {
	stub gh 'echo "[]"'
	stub python3 'echo partial; exit 1'
	run bash tools/build/plan-dependency-prs.bash
	[ "$status" -ne 0 ]
	[ ! -s "$GITHUB_OUTPUT" ]
}

@test "each major PR has one upgrade and one PR collects minors and patches across ecosystems" {
	PLANNER="$project/tools/build/upgrade_dependencies.py" python3 - <<'PY'
import os
import runpy
from pathlib import Path
from unittest.mock import patch

planner = runpy.run_path(os.environ["PLANNER"])
os.chdir(os.environ["BATS_TEST_TMPDIR"])
# The same library appears with different pins in two Maven installations.
Path("MODULE.bazel").write_text('    "org:lib:1.0.0",\n    "org:lib:2.0.0",\n')
with patch.dict(planner["discover"].__globals__, {
    "mise_tools": lambda: {"a": "1.0", "b": "1.0", "c": "1.0", "d": "1.0"},
    "releases": lambda *_: ["1.0.1", "2.1", "3.0"],
}):
    minor, majors = planner["discover"]()
existing = planner["major_branch"]("mise", "d")
jobs = planner["matrix"](minor, majors, {existing})["include"]
major_jobs = [job for job in jobs if job["branch"] != planner["MINOR_BRANCH"]]
assert len(major_jobs) == 3
assert existing in [job["branch"] for job in major_jobs]
for job in major_jobs:
    change, = job["changes"]
    assert change[2].split(".")[0] != change[3].split(".")[0]
assert len({job["changes"][0][1] for job in major_jobs}) == 3
minor_job, = [job for job in jobs if job["branch"] == planner["MINOR_BRANCH"]]
assert len(minor_job["changes"]) == 6
assert {c[0] for c in minor_job["changes"]} == {"bazel", "mise"}
assert {tuple(c[2:]) for c in minor_job["changes"]} == {("1.0", "1.0.1"), ("1.0.0", "1.0.1"), ("2.0.0", "2.1")}
PY
}
