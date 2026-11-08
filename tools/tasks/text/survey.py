#!/usr/bin/env -S uv run --script --quiet
# SPDX-License-Identifier: MIT
# MISE description="Compare a sample of the corpus with EUR-Lex's HTML, as text:compare does one page, and rank the patterns by how many texts have them"
# USAGE include file="$MISE_CONFIG_ROOT/tools/tasks/text/survey.usage.kdl"
# /// script
# requires-python = ">=3.14"
# dependencies = ["playwright==1.64.0"]
# ///
"""text:compare over a sample of the corpus: which differences from EUR-Lex are rules of ours to fix."""

import argparse
import contextlib
import gzip
import hashlib
import http.server
import io
import os
import re
import shutil
import subprocess
import sys
import threading
import time
from collections import defaultdict
from dataclasses import dataclass
from functools import partial
from pathlib import Path

# text:compare's functions, without leaving its bytecode beside it
sys.dont_write_bytecode = True
import compare
from playwright.sync_api import sync_playwright

ROOT = compare.ROOT
OUT = compare.OUT / "survey"
CONVERT = "//formex-converter/src/test/java/dev/groknull/kludgeworks/formex/converter:convert_texts"
RENDER = "//viewer/src/test/java/dev/groknull/kludgeworks/eu/legislation/legaltext:render_texts"
CSS = ROOT / "viewer/src/main/resources/static/css/viewer.css"
LARGEST = 4 * 1024 * 1024  # bytes of Formex: past this, a text takes minutes to compare
SCHEMA = re.compile(rb"formex-(\d\d\.\d\d)")
ROOT_ELEMENT = re.compile(rb"<([A-Z][A-Z0-9.]*)[\s>/]")
TYPES = {"R": "regulation", "L": "directive", "D": "decision"}
# how a line is set, not what it says: the stylesheet's to fix, apart from the text's differences
SETTING = {"aligned", "styled"}


@dataclass(frozen=True)
class Candidate:
    id: str
    celex: str
    formex: str
    size: int

    @property
    def kind(self) -> str:
        return "consolidated" if self.celex.startswith("0") else "original"

    @property
    def type(self) -> str:
        return TYPES.get(self.celex[5:6], "other")

    @property
    def act(self) -> str:
        """The act it is a version of: its CELEX without the sector and a consolidation's date."""
        return self.celex[1:].split("-")[0]

    @property
    def stratum(self) -> tuple[str, str, str]:
        return self.kind, self.formex, self.type


def head(file: Path) -> bytes:
    """A file's first bytes, unzipped if gzipped (the mirror keeps some consolidations so)."""
    with file.open("rb") as raw:
        start = raw.read(2)
        raw.seek(0)
        stream = gzip.GzipFile(fileobj=raw) if start == b"\x1f\x8b" else raw
        return stream.read(2048)


def candidate(mirror: Path, id: str, celex: str) -> Candidate | None:
    """A manifestation, with the Formex version of the file the converter reads (as
    Manifestation.input chooses it) and its size; none if it isn't an act or a consolidation."""
    if not re.fullmatch(r"[03]\d{4}[A-Z][0-9A-Z()]+(-\d{8})?", celex):
        return None
    files = [f for f in (mirror / "files" / id).iterdir() if f.suffix == ".xml"]
    if not files:
        return None
    heads = {f: head(f) for f in files}
    roots = {
        f: (m.group(1).decode() if (m := ROOT_ELEMENT.search(h)) else "") for f, h in heads.items()
    }
    main = (
        next((f for f in files if roots[f].startswith("CONS.")), None)
        or next((f for f in files if roots[f] == "DOC"), None)
        or max(files, key=lambda f: f.stat().st_size)
    )
    version = SCHEMA.search(heads[main])
    size = sum(f.stat().st_size for f in files)
    if not version or size > LARGEST:
        return None
    return Candidate(id, celex, version.group(1).decode(), size)


def sample(mirror: Path, size: int) -> list[Candidate]:
    """size texts across the strata, spread across sizes in each, one version of an act at most."""
    candidates = []
    for line in (mirror / "manifest.tsv").read_text(encoding="utf-8").splitlines():
        id, _, celex = (line.split("\t") + ["", ""])[:3]
        if c := candidate(mirror, id, celex):
            candidates.append(c)
    strata = defaultdict(list)
    for c in candidates:
        strata[c.stratum].append(c)
    print(f"{len(candidates)} candidates in {len(strata)} strata", file=sys.stderr)
    # each stratum by size, ties in a stable shuffle: the first pick the median, each next the one
    # farthest from the sizes taken, among those of an act not taken yet
    median = {}
    for key, members in strata.items():
        members.sort(key=lambda c: (c.size, hashlib.sha256(c.id.encode()).hexdigest()))
        median[key] = members[len(members) // 2].size
    chosen: list[Candidate] = []
    acts: set[str] = set()
    taken: dict[tuple, list[int]] = defaultdict(list)
    while len(chosen) < size:
        progressed = False
        for key in sorted(strata, key=lambda k: len(taken[k])):
            if len(chosen) >= size:
                break
            free = [c for c in strata[key] if c.act not in acts]
            if not free:
                continue
            if taken[key]:
                pick = max(free, key=lambda c: min(abs(c.size - s) for s in taken[key]))
            else:
                pick = min(free, key=lambda c: abs(c.size - median[key]))
            chosen.append(pick)
            acts.add(pick.act)
            taken[key].append(pick.size)
            progressed = True
        if not progressed:
            break
    return sorted(chosen, key=lambda c: c.stratum)


def bazel_run(target: str, *args: str) -> None:
    subprocess.run(
        ["bazel", "run", "--ui_event_filters=-info", "--noshow_progress", target, "--", *args],
        check=True,
        cwd=ROOT,
    )


@contextlib.contextmanager
def served(folder: Path):
    """folder over HTTP on a free port of the loopback, for as long as the block runs."""
    handler = partial(QuietHandler, directory=str(folder))
    server = http.server.ThreadingHTTPServer(("127.0.0.1", 0), handler)
    thread = threading.Thread(target=server.serve_forever, daemon=True)
    thread.start()
    try:
        yield f"http://127.0.0.1:{server.server_port}"
    finally:
        server.shutdown()


class QuietHandler(http.server.SimpleHTTPRequestHandler):
    def log_message(self, format, *args):
        pass


def survey(pages: Path, language: str, strata: dict[str, Candidate]) -> None:
    patterns: dict[tuple, list[tuple[str, compare.Difference]]] = defaultdict(list)
    texts = []
    subprocess.run(
        [sys.executable, "-m", "playwright", "install", "--only-shell", "chromium"], check=True
    )
    celexes = sorted(p.name for p in pages.iterdir() if (p / language / "index.html").exists())
    with served(pages) as base, sync_playwright() as playwright:
        browser = playwright.chromium.launch()
        for n, celex in enumerate(celexes, 1):
            started = time.monotonic()
            url = f"{base}/{celex}/{language}/"
            try:
                html = compare.reference(celex, language)
                if html is None:
                    raise Skipped("Cellar has no xhtml of it")
                _, ours = compare.our_page(browser, url)
                theirs = compare.their_page(browser, html)
            except Skipped as e:
                texts.append((celex, None, str(e)))
                print(f"[{n}/{len(celexes)}] {celex}: not compared, {e}", file=sys.stderr)
                continue
            except Exception as e:  # noqa: BLE001 (a page that breaks the browser is a finding, not a stop)
                texts.append((celex, None, f"not read: {type(e).__name__}"))
                print(f"[{n}/{len(celexes)}] {celex}: not read, {e}", file=sys.stderr)
                continue
            differences = compare.compare(theirs, ours)
            with contextlib.redirect_stdout(io.StringIO()):
                compare.report(url, celex, theirs, ours, differences)
            for d in differences:
                patterns[d.pattern].append((celex, d))
            set_ = sum(d.kind in SETTING for d in differences)
            texts.append((celex, (len(theirs), len(ours), len(differences) - set_, set_), ""))
            print(
                f"[{n}/{len(celexes)}] {celex}: {len(differences) - set_} differences of text and"
                f" {set_} of setting in {len(theirs)} lines, {time.monotonic() - started:.0f}s",
                file=sys.stderr,
            )
        browser.close()
    summarise(patterns, texts, strata)


class Skipped(Exception):
    """A text there is nothing to compare with."""


def summarise(patterns: dict, texts: list, strata: dict[str, Candidate]) -> None:
    compared = [t for t in texts if t[1]]
    ranked = sorted(patterns.items(), key=lambda p: (-len({c for c, _ in p[1]}), -len(p[1])))
    groups = [
        ("The text's differences", [p for p in ranked if p[0][0] not in SETTING]),
        ("How lines are set", [p for p in ranked if p[0][0] in SETTING]),
    ]
    path = OUT / "summary.md"
    with path.open("w", encoding="utf-8") as out:
        out.write(f"# text:survey\n\n{len(compared)} texts compared of {len(texts)};")
        out.write(f" {len(ranked)} patterns, ranked by how many texts have them.\n")
        for title, group in groups:
            out.write(f"\n## {title}\n\n")
            out.write("| Texts | Differences | Kind | EUR-Lex | Ours |\n|---:|---:|---|---|---|\n")
            for (kind, theirs, ours), ds in group:
                out.write(
                    f"| {len({c for c, _ in ds})} | {len(ds)} | {kind} | `{cell(theirs)}` | `{cell(ours)}` |\n"
                )
        for title, group in groups:
            out.write(f"\n## Examples: {title.lower()}\n")
            for (kind, theirs, ours), ds in group:
                by_text = {}
                for celex, d in ds:
                    by_text.setdefault(celex, d)
                out.write(f"\n### {kind}: EUR-Lex `{theirs or '—'}`, ours `{ours or '—'}`\n\n")
                out.write(f"{len(by_text)} texts, {len(ds)} differences\n\n")
                for celex, d in list(by_text.items())[:3]:
                    out.write(
                        f"- {celex}, line {d.at}" + (f": {d.detail}" if d.detail else "") + "\n"
                    )
                    for line in d.theirs:
                        out.write(f"  - EUR-Lex: `{compare._text((line,), 120)}`\n")
                    for line in d.ours:
                        out.write(f"  - ours: `{compare._text((line,), 120)}`\n")
        out.write("\n## Texts\n\n| CELEX | Kind | Formex | Type | KB | EUR-Lex lines | Ours |")
        out.write(" Of text | Of setting |\n|---|---|---|---|---:|---:|---:|---:|---:|\n")
        for celex, counts, why in texts:
            c = strata.get(celex)
            facts = f"{c.kind} | {c.formex} | {c.type} | {c.size // 1024}" if c else "| | |"
            numbers = " | ".join(map(str, counts)) if counts else f"{why} | | |"
            out.write(f"| {celex} | {facts} | {numbers} |\n")
    print(f"\n{len(compared)} texts compared, {len(ranked)} patterns: {path.relative_to(ROOT)}")
    for title, group in groups:
        print(f"\n{title}:")
        for (kind, theirs, ours), ds in group[:12]:
            print(
                f"  {len({c for c, _ in ds}):>4} texts {len(ds):>6}  {kind:<9} EUR-Lex: {theirs or '—'}  ours: {ours or '—'}"
            )


def cell(made: str) -> str:
    """Elements' names in a table's cell: none as a dash, a pipe escaped."""
    return (made or "—").replace("|", "\\|")


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__.splitlines()[0])
    parser.add_argument("--language", default=os.environ.get("usage_language", "ENG"))
    parser.add_argument("--size", type=int, default=int(os.environ.get("usage_size", "200")))
    parser.add_argument(
        "--compare-only",
        action="store_true",
        default=os.environ.get("usage_compare_only") == "true",
    )
    args = parser.parse_args()
    language = args.language.lower()
    mirror = ROOT / "formex-corpus" / language
    if not (mirror / "manifest.tsv").is_file():
        sys.exit(
            f"text:survey: no corpus at {mirror}: run mise run corpus:fetch --language {args.language} first"
        )
    texts, pages = OUT / "legal-text", OUT / "pages"
    listed = OUT / "sample.tsv"
    if not args.compare_only:
        chosen = sample(mirror, args.size)
        OUT.mkdir(parents=True, exist_ok=True)
        listed.write_text(
            "".join(
                f"{c.id}\t{c.celex}\t{c.kind}\t{c.formex}\t{c.type}\t{c.size}\n" for c in chosen
            ),
            encoding="utf-8",
        )
        print(f"{len(chosen)} texts sampled: {listed.relative_to(ROOT)}", file=sys.stderr)
        shutil.rmtree(texts, ignore_errors=True)
        shutil.rmtree(pages, ignore_errors=True)
        bazel_run(CONVERT, str(mirror), str(listed), str(texts))
        bazel_run(RENDER, str(texts), str(pages), language)
        (pages / "css").mkdir(parents=True, exist_ok=True)
        shutil.copy(CSS, pages / "css" / "viewer.css")
    strata = {}
    for line in listed.read_text(encoding="utf-8").splitlines():
        id, celex, _, formex, _, size = line.split("\t")
        strata[celex] = Candidate(id, celex, formex, int(size))
    survey(pages, language, strata)


if __name__ == "__main__":
    main()
