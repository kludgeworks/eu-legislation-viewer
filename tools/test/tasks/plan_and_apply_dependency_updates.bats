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
	PLANNER="$project/tools/build/upgrade_dependencies.py" python3 -B - <<'PY'
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
obsolete = planner["major_branch"]("mise", "retired")
active = {existing, obsolete}
refreshed = planner["matrix"](minor, majors, active)["include"]
assert len(refreshed) == 4
assert next(j for j in refreshed if j["branch"] == obsolete)["changes"] == []
assert any(j["branch"] == existing for j in refreshed)
# Once the major merges, its old same-major patch is no longer in the refreshed minor PR.
with patch.dict(planner["discover"].__globals__, {
    "mise_tools": lambda: {"a": "1.0", "b": "1.0", "c": "1.0", "d": "3.0"},
    "releases": lambda *_: ["1.0.1", "2.1", "3.0"],
}):
    after_merge = planner["matrix"](*planner["discover"](), set())["include"]
assert all(c[1] != "d" for j in after_merge for c in j["changes"])
assert existing in [job["branch"] for job in major_jobs]
for job in major_jobs:
    change, = job["changes"]
    assert change[2].split(".")[0] != change[3].split(".")[0]
    assert len(job["body"].splitlines()) == 3
    assert f"| `{change[1]}` | `{change[2]}` | `{change[3]}` | Major |" in job["body"]
assert len({job["changes"][0][1] for job in major_jobs}) == 3
minor_job, = [job for job in jobs if job["branch"] == planner["MINOR_BRANCH"]]
assert len(minor_job["changes"]) == 6
assert len(minor_job["body"].splitlines()) == 8
assert "| Maven | `org:lib` | `2.0.0` | `2.1` | Minor |" in minor_job["body"]
assert "| mise | `a` | `1.0` | `1.0.1` | Patch |" in minor_job["body"]
assert {c[0] for c in minor_job["changes"]} == {"bazel", "mise"}
assert {tuple(c[2:]) for c in minor_job["changes"]} == {("1.0", "1.0.1"), ("1.0.0", "1.0.1"), ("2.0.0", "2.1")}
PY
}

@test "action releases join the shared PR plan and pinact updates matching workflow and composite uses" {
	PLANNER="$project/tools/build/upgrade_dependencies.py" python3 -B - <<'PY'
import os
import runpy
import subprocess
from pathlib import Path
from unittest.mock import patch

planner = runpy.run_path(os.environ["PLANNER"])
os.chdir(os.environ["BATS_TEST_TMPDIR"])
Path("MODULE.bazel").touch()
workflow = Path(".github/workflows/ci.yml")
composite = Path(".github/actions/setup/action.yaml")
old_sha, new_sha = "a"*40, "b"*40
for path in (workflow, composite):
    path.parent.mkdir(parents=True)
    path.write_text(f"  - uses: owner/project/sub@{old_sha} # v1.0.0\n  - uses: ./local\n")
subprocess.run(["git", "init", "-q"], check=True)
subprocess.run(["git", "add", ".github"], check=True)
def pinact(args, **kwargs):
    assert args[-2:] == [composite, workflow]
    if "--diff-file" in args:
        assert kwargs["input"].count(b"+  - uses: owner/project/sub@v1.2.0") == 2
        for path in args[-2:]:
            path.write_text(path.read_text().replace("@v1.2.0", f"@{new_sha} # v1.2.0"))
    else:
        assert args[:4] == ["pinact", "run", "--fix=false", "--no-api"]

# Keep the real git diff and stub only the two pinact calls.
real_run = subprocess.run
def run(args, **kwargs):
    return pinact(args, **kwargs) if args[0] == "pinact" else real_run(args, **kwargs)
with patch.dict(planner["discover"].__globals__, {
    "mise_tools": lambda: {},
    "action_tags": lambda repo: {"1.2.0":"v1.2.0", "2.0.0":"v2.0.0"},
}), patch.object(subprocess, "run", side_effect=run):
    minor, majors = planner["discover"]()
    assert minor == [["action", "owner/project", "1.0.0", "1.2.0"]]
    assert list(majors.values()) == [["action", "owner/project", "1.0.0", "2.0.0"]]
    planner["apply"](minor)
for path in (workflow, composite):
    assert path.read_text() == f"  - uses: owner/project/sub@{new_sha} # v1.2.0\n  - uses: ./local\n"
PY
}

@test "reports uncovered pins and separates calendar releases from numeric Maven versions" {
	PLANNER="$project/tools/build/upgrade_dependencies.py" python3 -B - <<'PY'
import os
import runpy
from pathlib import Path
from unittest.mock import patch

p = runpy.run_path(os.environ["PLANNER"])
os.chdir(os.environ["BATS_TEST_TMPDIR"])
Path(".bazelversion").write_text("9.3.1")
Path("MODULE.bazel").write_text('    "g:ordinary:2.1",\n    "g:bom:2025.0.2",\n    "g:builder:53",\n    "g:zstd:1.5.7-4",\n')
os.environ["GITHUB_STEP_SUMMARY"] = "summary"
versions = {"g:ordinary": ["2.2", "20030203"], "g:bom": ["2026.0.1"], "g:builder": ["54"], "tool": ["v1.0.1"]}
with patch.dict(p["discover"].__globals__, {
    "mise_tools": lambda: {"node":"24.0.0", "tool":"1.0.0"},
    "releases": lambda kind, name: versions[name],
}):
    minor, majors = p["discover"]()
assert [c[-1] for c in minor] == ["2.2", "1.0.1"]
assert sorted(c[-1] for c in majors.values()) == ["2026.0.1", "54"]
summary = Path("summary").read_text()
assert all(s in summary for s in ["20030203", "Bazel 9.3.1", "node@24.0.0", "g:zstd@1.5.7-4"])
PY
}

@test "publisher verifies the planned action SHA and rejects extra workflow edits or files" {
	make_repo
	cd "$repo"
	VERIFY="$project/tools/build" python3 -B - <<'PY'
import os, sys, subprocess
from pathlib import Path
from unittest.mock import patch

sys.path.insert(0, os.environ["VERIFY"])
import verify_dependency_patch as publisher
def git(*args):
    return subprocess.check_output(["git", *args])
path = Path(".github/workflows/test.yml")
path.parent.mkdir(parents=True)
old = "  - uses: owner/action@" + "a"*40 + " # v1.0.0\n"
new = old.replace("a"*40, "b"*40).replace("1.0.0", "1.1.0")
path.write_text(old)
git("add", ".")
git("commit", "-qm", "fixture")
changes = [["action","owner/action","1.0.0","1.1.0"]]
artifact = Path(os.environ["BATS_TEST_TMPDIR"]) / "artifact.patch"
for contents, extra, valid in [(new, False, True), (new + "  - run: echo extra\n", False, False),
                               (new.replace("b"*40,"c"*40), False, False), (new, True, False)]:
    path.write_text(contents)
    if extra:
        Path("extra").write_text("unplanned")
    git("add", "-A")
    artifact.write_bytes(git("diff", "--cached", "--binary"))
    git("reset", "--hard", "-q", "HEAD")
    with patch.object(publisher, "release_pin", return_value="b"*40 + " # v1.1.0"):
        try:
            publisher.verify_patch(artifact, changes)
        except ValueError:
            assert not valid
            assert path.read_text() == old
        else:
            assert valid and path.read_text() == new
    git("reset", "--hard", "-q", "HEAD")
artifact.write_bytes(b"")
publisher.verify_patch(artifact, [])
assert not git("diff","--cached")
PY
}
