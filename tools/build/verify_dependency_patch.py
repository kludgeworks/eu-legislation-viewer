# SPDX-License-Identifier: MIT
"""Validate a read-only job's patch against the plan before materializing any files."""

import json
import re
import subprocess
import sys
from functools import cache
from pathlib import Path

from upgrade_dependencies import ACTION_PIN, action_files, action_tags, updated_mise, updated_module


def git(*args):
    return subprocess.check_output(["git", *args])


@cache
def release_pin(repo, version):
    tag = action_tags(repo)[version]
    sha = subprocess.check_output(
        ["gh", "api", f"repos/{repo}/commits/{tag}", "--jq", ".sha"], text=True
    ).strip()
    if not re.fullmatch("[a-f0-9]{40}", sha):
        raise ValueError(f"Invalid commit for {repo}@{tag}")
    return f"{sha} # {tag}"


def verify_mise_lock(before, after, planned):
    if {k: v for k, v in before.items() if k != "tools"} != {
        k: v for k, v in after.items() if k != "tools"
    }:
        raise ValueError("Unplanned mise lock metadata change")
    old_tools, new_tools = before["tools"], after["tools"]
    if old_tools.keys() != new_tools.keys():
        raise ValueError("Unplanned change to the locked mise tool inventory")
    for name, old_entries in old_tools.items():
        new_entries = new_tools[name]
        if name not in planned:
            if new_entries != old_entries:
                raise ValueError(f"Unplanned mise lock change for {name}")
            continue
        old, new = planned[name]
        previous = [entry for entry in old_entries if entry["version"] == old]
        updated = [entry for entry in new_entries if entry["version"] == new]
        if (
            len(previous) != 1
            or len(updated) != 1
            or updated[0]["backend"] != previous[0]["backend"]
            or updated[0]["specifiers"] != [new]
            or [e for e in old_entries if e["version"] != old]
            != [e for e in new_entries if e["version"] != new]
        ):
            raise ValueError(f"Unexpected locked versions or backend for {name}")
    if not planned.keys() <= new_tools.keys():
        raise ValueError("Missing planned mise tool in lock")


def maven_sources(lock):
    # Repository entries can identify a classifier, e.g. org.xmlresolver:xmlresolver:jar:data.
    sources = {}
    for url, coordinates in lock["repositories"].items():
        for coordinate in coordinates:
            artifact = ":".join(coordinate.split(":")[:2])
            sources.setdefault(artifact, {}).setdefault(coordinate, []).append(url)
    return sources


def verify_maven_lock(before, after):
    if (
        after["version"] != before["version"]
        or not after["repositories"].keys() <= before["repositories"].keys()
    ):
        raise ValueError("Unexpected Maven lock format or repository URL")
    old_sources, new_sources = maven_sources(before), maven_sources(after)
    for coordinate in before["artifacts"].keys() & after["artifacts"].keys():
        old, new = before["artifacts"][coordinate], after["artifacts"][coordinate]
        if old["version"] == new["version"] and (
            old != new or old_sources.get(coordinate) != new_sources.get(coordinate)
        ):
            raise ValueError(f"Immutable Maven artifact changed: {coordinate}:{old['version']}")


def verify_patch(patch, changes, warnings=None):
    mise = {n: (o, v) for kind, n, o, v in changes if kind == "mise"}
    bazel = {(n, o): v for kind, n, o, v in changes if kind == "bazel"}
    actions = {(n, o): v for kind, n, o, v in changes if kind == "action"}
    expected = {}
    lockfiles = set()
    if mise:
        expected["mise.toml"] = updated_mise(Path("mise.toml").read_text(), mise)
        lockfiles.add("mise.lock")
    if bazel:
        expected["MODULE.bazel"] = updated_module(Path("MODULE.bazel").read_text(), bazel)
        lockfiles.update(("MODULE.bazel.lock", "maven_install.json"))

    found = set()

    def action_ref(match):
        key = (match["repo"], match["version"])
        if key not in actions:
            return match[0]
        found.add(key)
        return match["uses"] + release_pin(key[0], actions[key])

    if actions:
        for path in action_files():
            before = path.read_text()
            after = ACTION_PIN.sub(action_ref, before)
            if before != after:
                expected[str(path)] = after
        if found != actions.keys():
            raise ValueError("Action plan does not match checkout")

    # Apply to the Git index only. Validate every entry before writing to the checkout.
    if patch.stat().st_size:
        git("apply", "--cached", "--binary", str(patch))
    changed = git("diff", "--cached", "--no-renames", "--name-only", "-z").decode().split("\0")[:-1]
    if not expected.keys() <= set(changed):
        raise ValueError("Patch omits planned pin changes")
    if mise and "mise.lock" not in changed:
        raise ValueError("Patch omits the planned mise lock update")
    for name in changed:
        path = Path(name)
        nested_lock = (
            len(path.parts) == 5
            and path.parts[:2] == (".mise", "locks")
            and path.name in {"aube-lock.yaml", "package.json", "pyproject.toml", "uv.lock"}
            and any(
                path.parts[2] == tool.replace(":", "-")
                and path.parts[3].split("~")[0] in versions
                and re.fullmatch(r"[0-9.]+(?:~[a-f0-9]+)?", path.parts[3])
                for tool, versions in mise.items()
            )
        )
        baseline = git("ls-tree", "HEAD", "--", name).split()
        if name not in expected and name not in lockfiles and not nested_lock:
            # Check if this is an existing tracked file (e.g. reformatted by tools)
            if not baseline:
                raise ValueError(f"Unplanned path: {name}")
            if warnings is not None:
                warnings.append(name)
            print(f"::warning::File outside lock/build files was modified: {name}", file=sys.stderr)
        entry = git("ls-files", "--stage", "--", name)
        # A deleted per-tool lock has no entry in the proposed index.
        if not entry and nested_lock:
            continue
        # The committed Maven lock is 100755. Preserve existing modes and only add 100644 files.
        mode = baseline[0] if baseline else b"100644"
        if mode not in {b"100644", b"100755"} or not entry.startswith(mode + b" "):
            raise ValueError(f"Missing file or unexpected mode: {name}")
        if name == "mise.lock":
            import tomllib

            verify_mise_lock(
                tomllib.loads(path.read_text()),
                tomllib.loads(git("show", f":{name}").decode()),
                mise,
            )
        elif name == "maven_install.json":
            verify_maven_lock(json.loads(path.read_bytes()), json.loads(git("show", f":{name}")))
        elif name.endswith(".json") or name == "MODULE.bazel.lock":
            json.loads(git("show", f":{name}"))
        if name in expected:
            actual = git("show", f":{name}").decode()
            matches = actual == expected[name]
            if not matches and name == "mise.toml":
                import tomllib

                # Taplo may align comments and whitespace after version strings change.
                matches = tomllib.loads(actual) == tomllib.loads(expected[name])
            if not matches:
                raise ValueError(f"Unexpected changes in {name}")

    # All entries are approved. Preserve deletions and new locks for the PR action.
    for name in changed:
        if git("ls-files", "--", name):
            git("checkout-index", "--force", "--", name)
        else:
            Path(name).unlink()
    return any(name.startswith(".github/workflows/") for name in changed)


if __name__ == "__main__":
    warnings = []
    workflows_changed = verify_patch(Path(sys.argv[1]), json.loads(sys.argv[2]), warnings=warnings)
    print(f"workflows_changed={str(workflows_changed).lower()}")
    if warnings:
        print(f"extra_files={json.dumps(warnings)}")
