# SPDX-License-Identifier: MIT
"""Plan and apply dependency PRs against a single revision of main."""

import argparse
import hashlib
import json
import re
import subprocess
from functools import cache
from pathlib import Path
from urllib.request import urlopen
from xml.etree import ElementTree

# Node tracks Bazel, hk and oxlint track config URLs, and Java tracks Temurin 25.
COORDINATED_TOOLS = {"node", "hk", "oxlint", "java"}
RELEASE = re.compile(r"[0-9]+(?:\.[0-9]+){0,2}")
MINOR_BRANCH = "automation/dependencies-minor"
MAJOR_PREFIX = "automation/dependencies-major/"
# Match direct bazel_dep declarations and explicit Maven pins only.
BAZEL_PINS = re.compile(
    r'^(?:bazel_dep\(name = "(?P<module>[\w.-]+)", version = "'
    r'|[ \t]*"(?P<maven>[\w.-]+:[\w.-]+):)(?P<version>[\d.]+)(?=")',
    re.MULTILINE,
)


def numbers(version):
    return tuple(map(int, version.split("."))) + (0,) * (2 - version.count("."))


def mise_tools():
    import tomllib

    tools = tomllib.loads(Path("mise.toml").read_text())["tools"]
    return {name: pin if isinstance(pin, str) else pin["version"] for name, pin in tools.items()}


def bazel_name(match):
    return match["module"] or match["maven"]


@cache
def releases(ecosystem, name):
    if ecosystem == "mise":
        result = subprocess.check_output(["mise", "ls-remote", name], text=True)
        return result.splitlines()
    if ":" in name:
        group, artifact = name.split(":")
        url = f"https://repo.maven.apache.org/maven2/{group.replace('.', '/')}/{artifact}"
        with urlopen(f"{url}/maven-metadata.xml", timeout=30) as response:
            return [
                v.text for v in ElementTree.parse(response).findall("versioning/versions/version")
            ]
    with urlopen(f"https://bcr.bazel.build/modules/{name}/metadata.json", timeout=30) as response:
        metadata = json.load(response)
    return [v for v in metadata["versions"] if v not in metadata.get("yanked_versions", {})]


def discover():
    module = Path("MODULE.bazel").read_text()
    pins = {("bazel", bazel_name(m), m["version"]) for m in BAZEL_PINS.finditer(module)}
    pins.update(("mise", n, v) for n, v in mise_tools().items() if n not in COORDINATED_TOOLS)
    minor = []
    majors = {}
    for ecosystem, name, current in sorted(pins):
        if not RELEASE.fullmatch(current):
            continue
        same, higher = [], []
        for v in releases(ecosystem, name):
            if RELEASE.fullmatch(v) and numbers(v) > numbers(current):
                (same if numbers(v)[0] == numbers(current)[0] else higher).append(v)
        if same:
            minor.append([ecosystem, name, current, max(same, key=numbers)])
        # PMD uses its own Saxon 12 engine (MODULE.bazel).
        if higher and not (name == "net.sf.saxon:Saxon-HE" and numbers(current)[0] == 12):
            # A dependency can have multiple distinct pins. Propose one upgrade per PR.
            majors.setdefault(
                (ecosystem, name), [ecosystem, name, current, max(higher, key=numbers)]
            )
    return minor, majors


def major_branch(ecosystem, name):
    slug = re.sub(r"[^a-z0-9.-]+", "-", name.lower())
    digest = hashlib.sha256(name.encode()).hexdigest()[:8]
    return f"{MAJOR_PREFIX}{ecosystem}-{slug}-{digest}"


def matrix(minor, majors, open_branches):
    candidates = {}
    for (ecosystem, name), change in sorted(majors.items()):
        branch = major_branch(ecosystem, name)
        candidates[branch] = {
            "branch": branch,
            "title": f"Upgrade {name} from {change[2]} to {change[3]}",
            "changes": [change],
        }
    active = sorted(b for b in open_branches if b.startswith(MAJOR_PREFIX))
    # An obsolete PR still occupies a slot until its branch is reconciled and the PR closes.
    selected = [
        candidates.get(b, {"branch": b, "title": "Obsolete major update", "changes": []})
        for b in active
    ]
    new = [pr for b, pr in candidates.items() if b not in active]
    selected.extend(new[: max(0, 3 - len(active))])
    selected.append(
        {"branch": MINOR_BRANCH, "title": "Upgrade minor and patch dependencies", "changes": minor}
    )
    return {"include": selected}


def apply(changes):
    mise = {name: (old, new) for kind, name, old, new in changes if kind == "mise"}
    if mise:
        before = mise_tools()
        expected = before.copy()
        for name, (old, new) in mise.items():
            if before[name] != old:
                raise ValueError(f"Stale mise pin for {name}")
            expected[name] = new
        subprocess.run(
            ["mise", "upgrade", "--no-global", "--bump", "--pin"]
            + [f"{name}@{new}" for name, (_, new) in mise.items()],
            check=True,
        )
        if mise_tools() != expected:
            raise ValueError("mise pins differ from the planned updates")
        subprocess.run(["mise", "install", "--locked"], check=True)
        subprocess.run(["taplo", "fmt", "mise.toml"], check=True)

    bazel = {(name, old): new for kind, name, old, new in changes if kind == "bazel"}
    if bazel:
        path = Path("MODULE.bazel")
        source = path.read_text()
        if not bazel.keys() <= {(bazel_name(m), m["version"]) for m in BAZEL_PINS.finditer(source)}:
            raise ValueError("Stale Bazel pins")

        def replace(match):
            version = match["version"]
            return match[0][: -len(version)] + bazel.get((bazel_name(match), version), version)

        path.write_text(BAZEL_PINS.sub(replace, source))
        subprocess.run(["mise", "run", "pin"], check=True)
        subprocess.run(["bazel", "mod", "deps", "--lockfile_mode=update"], check=True)


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("operation", choices=["plan", "apply"])
    parser.add_argument(
        "input_json", help="Open PR branch names for plan, or selected changes for apply"
    )
    args = parser.parse_args()
    data = json.loads(args.input_json)
    if args.operation == "plan":
        print(json.dumps(matrix(*discover(), set(data))))
    else:
        apply(data)
