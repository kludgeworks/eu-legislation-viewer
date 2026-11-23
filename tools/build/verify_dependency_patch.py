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


def verify_patch(patch, changes):
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
        if name not in expected and name not in lockfiles and not nested_lock:
            raise ValueError(f"Unplanned path: {name}")
        entry = git("ls-files", "--stage", "--", name)
        if not entry and nested_lock:
            continue
        # The committed Maven lock is 100755. Preserve existing modes and only add 100644 files.
        baseline = git("ls-tree", "HEAD", "--", name).split()
        mode = baseline[0] if baseline else b"100644"
        if mode not in {b"100644", b"100755"} or not entry.startswith(mode + b" "):
            raise ValueError(f"Missing file or unexpected mode: {name}")
        if name.endswith(".json") or name == "MODULE.bazel.lock":
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


if __name__ == "__main__":
    verify_patch(Path(sys.argv[1]), json.loads(sys.argv[2]))
