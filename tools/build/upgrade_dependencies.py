# SPDX-License-Identifier: MIT
# MISE description="Plan updates to mise tools, Bazel dependencies and GitHub Actions, or apply a selected plan"
"""The plan command reads open PR branch names and emits a GitHub Actions job matrix.
The apply command updates the selected pins and refreshes their lockfiles in this checkout.
"""

import argparse
import hashlib
import json
import os
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
    r'|[ \t]*"(?P<maven>[\w.-]+:[\w.-]+):)(?P<version>[^"\s:]+)(?=")',
    re.MULTILINE,
)


# The repository uses one SHA-pinned uses: reference per line with an exact release tag.
ACTION_PIN = re.compile(
    r"^(?P<uses>[ \t]*(?:-[ \t]+)?uses:[ \t]*(?P<repo>[\w.-]+/[\w.-]+)(?:/[\w./-]+)?@)"
    r"[0-9a-f]{40}[ \t]+#[ \t]*v?(?P<version>[0-9]+\.[0-9]+\.[0-9]+)[ \t]*$",
    re.MULTILINE,
)


def action_files():
    root = Path(".github")
    return sorted(
        p
        for p in [*root.glob("workflows/*"), *root.glob("actions/**/action.y*ml")]
        if p.suffix in (".yml", ".yaml") and p.is_file()
    )


@cache
def action_tags(repo):
    tags = subprocess.check_output(
        [
            "gh",
            "api",
            "--paginate",
            f"repos/{repo}/releases?per_page=100",
            "--jq",
            ".[] | select(.draft == false and .prerelease == false) | .tag_name",
        ],
        text=True,
    ).splitlines()
    return {tag.removeprefix("v"): tag for tag in tags if re.fullmatch(r"v?\d+\.\d+\.\d+", tag)}


def upgrade_actions(changes):
    targets = {(repo, old): new for kind, repo, old, new in changes if kind == "action"}
    if not targets:
        return
    seen = set()
    paths = []

    def replace(match):
        key = (match["repo"], match["version"])
        if key not in targets:
            return match[0]
        seen.add(key)
        tag = action_tags(match["repo"])[targets[key]]
        return match["uses"] + tag

    for path in action_files():
        before = path.read_text()
        after = ACTION_PIN.sub(replace, before)
        if after != before:
            path.write_text(after)
            paths.append(path)
    if seen != targets.keys():
        raise ValueError("Stale GitHub Actions pins")

    # Resolve only the selected release tags, including repeated uses in composite actions.
    diff = subprocess.check_output(["git", "diff", "--", *paths])
    subprocess.run(["pinact", "run", "--diff-file", "-", *paths], input=diff, check=True)
    subprocess.run(["pinact", "run", "--fix=false", "--no-api", *paths], check=True)


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
    if ecosystem == "action":
        return action_tags(name)
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


def version_scheme(version):
    first = version.split(".")[0]
    if len(first) == 8 and first.startswith(("19", "20")):
        return "date"
    if len(first) == 4 and first.startswith(("19", "20")):
        return "year"
    return "release"


def report_skipped(skipped):
    summary = os.environ.get("GITHUB_STEP_SUMMARY")
    if summary and skipped:
        with open(summary, "a") as output:
            print("### Pins outside this update plan\n", file=output)
            for pin, reason in sorted(skipped):
                print(f"- `{pin}` — {reason}", file=output)


def discover():
    module = Path("MODULE.bazel").read_text()
    pins = {("bazel", bazel_name(m), m["version"]) for m in BAZEL_PINS.finditer(module)}
    tools = mise_tools()
    skipped = [
        (f"{n}@{v}", "coordinated toolchain pin")
        for n, v in tools.items()
        if n in COORDINATED_TOOLS
    ]
    bazel_version = Path(".bazelversion")
    if bazel_version.is_file():
        skipped.append(
            (f"Bazel {bazel_version.read_text().strip()}", "Bazel runtime is maintained separately")
        )
    pins.update(("mise", n, v) for n, v in tools.items() if n not in COORDINATED_TOOLS)
    for path in action_files():
        pins.update(
            ("action", m["repo"], m["version"]) for m in ACTION_PIN.finditer(path.read_text())
        )
    minor = []
    majors = {}
    for ecosystem, name, current in sorted(pins):
        if not RELEASE.fullmatch(current):
            skipped.append((f"{name}@{current}", "unsupported version format"))
            continue
        same, higher = [], []
        for v in releases(ecosystem, name):
            if ecosystem == "mise":
                v = v.removeprefix("v")
            if not RELEASE.fullmatch(v) or numbers(v) <= numbers(current):
                continue
            if version_scheme(v) != version_scheme(current):
                skipped.append((f"{name}@{v}", f"different version scheme from {current}"))
            else:
                (same if numbers(v)[0] == numbers(current)[0] else higher).append(v)
        if same:
            minor.append([ecosystem, name, current, max(same, key=numbers)])
        # PMD uses its own Saxon 12 engine (MODULE.bazel).
        if higher and not (name == "net.sf.saxon:Saxon-HE" and numbers(current)[0] == 12):
            # A dependency can have multiple distinct pins. Propose one upgrade per PR.
            majors.setdefault(
                (ecosystem, name), [ecosystem, name, current, max(higher, key=numbers)]
            )
    report_skipped(skipped)
    return minor, majors


def major_branch(ecosystem, name):
    slug = re.sub(r"[^a-z0-9.-]+", "-", name.lower())
    digest = hashlib.sha256(name.encode()).hexdigest()[:8]
    return f"{MAJOR_PREFIX}{ecosystem}-{slug}-{digest}"


def pr_body(changes):
    if not changes:
        return "All proposed updates are already resolved on `main`."
    rows = [
        "| Source | Dependency | From | To | Update |",
        "| --- | --- | --- | --- | --- |",
    ]
    for ecosystem, name, old, new in changes:
        source = {"action": "GitHub Actions", "bazel": "Bazel", "mise": "mise"}[ecosystem]
        if ecosystem == "bazel" and ":" in name:
            source = "Maven"
        level = next(
            label
            for label, before, after in zip(("Major", "Minor", "Patch"), numbers(old), numbers(new))
            if before != after
        )
        rows.append(f"| {source} | `{name}` | `{old}` | `{new}` | {level} |")
    return "\n".join(rows)


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
    for job in selected:
        job["body"] = pr_body(job["changes"])
    return {"include": selected}


def updated_module(source, targets):
    if not targets.keys() <= {(bazel_name(m), m["version"]) for m in BAZEL_PINS.finditer(source)}:
        raise ValueError("Stale Bazel pins")

    def replace(match):
        version = match["version"]
        return match[0][: -len(version)] + targets.get((bazel_name(match), version), version)

    return BAZEL_PINS.sub(replace, source)


def updated_mise(source, targets):
    # Retain comments and inline tool options when mise expands registry defaults in its output.
    lines = source.splitlines(keepends=True)
    in_tools = False
    pending = targets.copy()
    for index, line in enumerate(lines):
        if line.startswith("["):
            in_tools = line.strip() == "[tools]"
        if in_tools and "=" in line:
            key, value = line.split("=", 1)
            name = key.strip().strip('"')
            if name in pending:
                old, new = pending.pop(name)
                value, count = re.subn(
                    rf'^(\s*(?:\{{\s*version\s*=\s*)?"){re.escape(old)}(")',
                    rf"\g<1>{new}\2",
                    value,
                    count=1,
                )
                if not count:
                    raise ValueError(f"Stale or unsupported mise declaration for {name}")
                lines[index] = f"{key}={value}"
    if pending:
        raise ValueError(f"Missing mise tools: {sorted(pending)}")
    return "".join(lines)


def apply(changes):
    upgrade_actions(changes)
    mise = {name: (old, new) for kind, name, old, new in changes if kind == "mise"}
    if mise:
        config = Path("mise.toml")
        planned_config = updated_mise(config.read_text(), mise)
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
        config.write_text(planned_config)
        subprocess.run(["mise", "install", "--locked"], check=True)
        subprocess.run(["taplo", "fmt", "mise.toml"], check=True)

    bazel = {(name, old): new for kind, name, old, new in changes if kind == "bazel"}
    if bazel:
        path = Path("MODULE.bazel")
        path.write_text(updated_module(path.read_text(), bazel))
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
