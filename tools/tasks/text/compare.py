#!/usr/bin/env -S uv run --script --quiet
# SPDX-License-Identifier: MIT
# MISE description="Compare the viewer's pages with EUR-Lex's HTML of the same texts, line by line"
# USAGE include file="$MISE_CONFIG_ROOT/tools/tasks/text/compare.usage.kdl"
# /// script
# requires-python = ">=3.14"
# dependencies = ["playwright==1.64.0"]
# ///
"""Our page of a version against EUR-Lex's HTML of it, as a reader sees both: line by line."""

import argparse
import difflib
import re
import subprocess
import sys
import urllib.error
import urllib.request
from collections import Counter, defaultdict
from dataclasses import dataclass
from pathlib import Path
from urllib.parse import urlparse

from playwright.sync_api import Browser, sync_playwright

ROOT = Path(__file__).resolve().parents[3]
OUT = ROOT / "text-compare"
CELLAR = "http://publications.europa.eu/resource/celex/"
USER_AGENT = "kludgeworks-viewer-dev (text:compare)"
WIDTH = 1280
RESYNC = 40  # how far ahead to look, on each side, for lines that agree again
INDENT = (
    24  # px: further apart than this from the column's left, two lines are indented differently
)
# EUR-Lex's stylesheets for a text shown in its page, as the Internet Archive has them
# (eur-lex.europa.eu answers scripts with a challenge): an OJ act's (oj- classes) and a consolidated
# version's. Each scopes its rules to the text's box, div#text.
WAYBACK = "http://web.archive.org/web/"
STYLESHEETS = {
    "oj-convex-act-embedded.css": "20231010173657id_/https://eur-lex.europa.eu/css/oj/oj-convex-act-embedded.css",
    "clg-embedded.css": "20251020165736id_/https://eur-lex.europa.eu/css/consleg/clg-embedded.css",
}
# what EUR-Lex's page around the text sets, which its stylesheets for the text don't: a column as
# wide as ours (Notion, EUR-Lex typography: about 830px) and its typeface
FRAME = """
div#text { width: 52rem; margin: 0 auto; font-family: "Times New Roman", Times, serif; line-height: 1.43; }
"""

# A page's lines, read in the browser: the text of each block under root (a line break starting
# another), blocks side by side joined: their boxes beside each other, at the same height (a table's
# cells, a grid's, though a margin lowers one's text), where a block within another is in it. A mark
# drawn before an element (::before, as a list's dash) is read as text: a grid's, beside what follows
# it; any other, at its place. Each line has the elements that made it (tag.class), for the patterns,
# how its first block aligns its text, how far from root's left its text starts (px), and how much of
# it is bold, italic.
LINES = """
root => {
  root = document.querySelector(root);
  const inline = new Set(["inline", "inline-block", "inline-flex", "inline-grid", "contents", "ruby"]);
  const block = el => {
    for (; el && el !== root; el = el.parentElement) {
      if (!inline.has(getComputedStyle(el).display)) return el;
    }
    return root;
  };
  // a text as shown: in capitals where its style sets them (Formex's HT TYPE="UC")
  const shown = node => {
    const transform = getComputedStyle(node.parentElement).textTransform;
    if (transform === "uppercase") return node.data.toUpperCase();
    if (transform === "lowercase") return node.data.toLowerCase();
    return node.data;
  };
  const name = el => el.tagName.toLowerCase() + [...el.classList].map(c => "." + c).join("");
  const left = root.getBoundingClientRect().left + parseFloat(getComputedStyle(root).paddingLeft);
  const measure = (g, node) => {
    const range = document.createRange();
    range.selectNodeContents(node);
    for (const r of range.getClientRects()) {
      if (r.width) g.from = Math.min(g.from, r.left);
    }
    const style = getComputedStyle(node.parentElement);
    const n = node.data.trim().length;
    g.chars += n;
    if (parseInt(style.fontWeight) >= 600) g.bold += n;
    if (style.fontStyle === "italic") g.italic += n;
  };
  const groups = [];
  let current = null;
  let mark = null;
  let markFrom = Infinity; // where a grid's mark starts: drawn, it has no text to measure
  const add = (b, text) => {
    if (!current || current.block !== b) {
      const box = b.getBoundingClientRect();
      current = { block: b, text: "", top: box.top, bottom: box.bottom, left: box.left, right: box.right,
                  align: getComputedStyle(b).textAlign, from: Infinity, chars: 0, bold: 0, italic: 0 };
      groups.push(current);
      if (mark) [current.text, current.from, mark] = [mark + " ", markFrom, null];
    }
    current.text += text;
  };
  const walker = document.createTreeWalker(root, NodeFilter.SHOW_TEXT | NodeFilter.SHOW_ELEMENT);
  for (let node; (node = walker.nextNode()); ) {
    if (node.nodeType === Node.ELEMENT_NODE) {
      if (node.tagName === "BR") current = null;
      const before = getComputedStyle(node, "::before").content;
      if (before.startsWith('"') && node.checkVisibility()) {
        const text = JSON.parse(before);
        if (["grid", "inline-grid"].includes(getComputedStyle(node).display)) {
          mark = text;
          markFrom = node.getBoundingClientRect().left + parseFloat(getComputedStyle(node).paddingLeft);
        } else add(block(node), text);
      }
      continue;
    }
    if (!node.parentElement.checkVisibility()) continue;
    const b = block(node.parentElement);
    // a space between inline elements is the line's; between blocks, nobody's
    if (node.data.trim() || (current && current.block === b)) {
      add(b, shown(node));
      measure(current, node);
    }
  }
  const lines = [];
  for (const g of groups) {
    const last = lines[lines.length - 1];
    const beside = last && g.left >= last.right - 1 && g.top < last.bottom && last.top < g.bottom;
    if (beside) {
      last.text += " " + g.text;
      last.made.push(name(g.block));
      Object.assign(last, { top: g.top, bottom: g.bottom, right: g.right });
      last.from = Math.min(last.from, g.from);
      for (const k of ["chars", "bold", "italic"]) last[k] += g[k];
    } else {
      lines.push({ ...g, made: [name(g.block)] });
    }
  }
  return lines.map(l => ({
    text: l.text, made: l.made.join(" + "), align: l.align,
    from: isFinite(l.from) ? l.from - left : null,
    chars: l.chars, bold: l.bold, italic: l.italic,
  }));
}
"""


@dataclass(frozen=True)
class Line:
    text: str
    made: str  # the elements that made it: p.oj-ti-grseq-1, div.num + div.heading
    align: str = ""  # left, centred, right: where its text is set across the column; "" if unknown
    indent: float = 0  # px from the column's left, its text's start
    face: str = ""  # bold, italic, bold italic: the face of most of its text

    @property
    def key(self) -> str:
        """Its text without spaces: the same for lines that differ only in spacing or breaks."""
        return re.sub(r"\s+", "", self.text)


@dataclass(frozen=True)
class Difference:
    kind: str
    theirs: tuple[Line, ...]
    ours: tuple[Line, ...]
    at: int  # the first of EUR-Lex's lines it concerns (or is before), counted from 1
    detail: str = ""  # how the same text is set: EUR-Lex right, ours left

    @property
    def pattern(self) -> tuple[str, str, str]:
        def made(lines: tuple[Line, ...]) -> str:
            return " | ".join(dict.fromkeys(_shape(line.made) for line in lines))

        return self.kind, made(self.theirs), made(self.ours)


def _shape(made: str) -> str:
    """Elements' names without what varies from one to the next: numbered classes, ids."""
    return re.sub(r"\.(?:d1e|id-)[\w-]+", "", made)


def normalise(text: str) -> str:
    return re.sub(r"\s+", " ", text).strip()


def _lines(read: list[dict]) -> list[Line]:
    return [
        Line(text, line["made"], *_setting(line), _face(line))
        for line in read
        if (text := normalise(line["text"]))
    ]


def _setting(line: dict) -> tuple[str, float]:
    """How a line is set across the column: its block's text-align, and how far in its text starts."""
    if line["from"] is None:
        return "", 0
    align = {"center": "centred", "right": "right", "end": "right"}.get(line["align"], "left")
    return align, line["from"]


def _face(line: dict) -> str:
    """The face of most of a line's text."""
    chars = line["chars"] or 1
    return " ".join(face for face in ("bold", "italic") if line[face] * 2 > chars)


def our_page(browser: Browser, url: str) -> tuple[str, list[Line]]:
    """Our page's CELEX, and its text's lines, once built (on the first read) and loaded by htmx."""
    context = browser.new_context(viewport={"width": WIDTH, "height": 1000})
    try:
        page = context.new_page()
        page.goto(url)
        page.wait_for_selector("article.legal-text", timeout=300_000)
        return page.inner_text(".version-celex").strip(), _lines(
            page.evaluate(LINES, "article.legal-text")
        )
    finally:
        context.close()


def their_page(browser: Browser, html: str) -> list[Line]:
    """EUR-Lex's page's lines, its scripts off: it is downloaded, and needs none to show its text."""
    context = browser.new_context(
        viewport={"width": WIDTH, "height": 1000}, java_script_enabled=False
    )
    try:
        page = context.new_page()
        # before its own: as the page is loaded, since a style added after waits for scripts
        page.set_content(f"<style>{stylesheets()}{FRAME}</style><div id='text'>{html}</div>")
        return _lines(page.evaluate(LINES, "div#text"))
    finally:
        context.close()


def stylesheets() -> str:
    """EUR-Lex's stylesheets, from the Internet Archive, fetched once."""
    css = []
    for name, archived in STYLESHEETS.items():
        path = OUT / "reference" / "css" / name
        if not path.exists():
            request = urllib.request.Request(WAYBACK + archived, headers={"User-Agent": USER_AGENT})
            # Target URL is safely prefixed with constant HTTPS WAYBACK archive origin
            # nosemgrep: python.lang.security.audit.dynamic-urllib-use-detected.dynamic-urllib-use-detected
            with urllib.request.urlopen(request, timeout=120) as response:
                body = response.read()
            path.parent.mkdir(parents=True, exist_ok=True)
            path.write_bytes(body)
            print(f"  fetched {name} from the Internet Archive", file=sys.stderr)
        css.append(path.read_text(encoding="cp1252"))  # as EUR-Lex writes them: Windows-1252
    return "\n".join(css)


def reference(celex: str, language: str) -> str | None:
    """EUR-Lex's HTML of the version, from Cellar, fetched once; none if Cellar has no xhtml of it."""
    path = OUT / "reference" / f"{celex}.{language}.xhtml"
    if not path.exists():
        request = urllib.request.Request(
            CELLAR + celex,
            headers={
                "Accept": "application/xhtml+xml",
                "Accept-Language": language,
                "User-Agent": USER_AGENT,
            },
        )
        try:
            # Target URL is safely prefixed with constant HTTPS CELLAR endpoint origin
            # nosemgrep: python.lang.security.audit.dynamic-urllib-use-detected.dynamic-urllib-use-detected
            with urllib.request.urlopen(request, timeout=120) as response:
                body = response.read()
        except urllib.error.HTTPError as e:
            if e.code in (404, 406):
                return None
            raise
        path.parent.mkdir(parents=True, exist_ok=True)
        path.write_bytes(body)
        print(f"  fetched {len(body) // 1024} KiB from {response.url}", file=sys.stderr)
    return path.read_text(encoding="utf-8")


def compare(theirs: list[Line], ours: list[Line]) -> list[Difference]:
    differences = []
    matcher = difflib.SequenceMatcher(
        None, [line.text for line in theirs], [line.text for line in ours], autojunk=False
    )
    for op, i1, i2, j1, j2 in matcher.get_opcodes():
        if op == "equal":
            differences += _set(theirs[i1:i2], ours[j1:j2], i1)
        elif op == "delete":
            differences += [Difference("missing", (theirs[i],), (), i + 1) for i in range(i1, i2)]
        elif op == "insert":
            differences += [Difference("extra", (), (ours[j],), i1 + 1) for j in range(j1, j2)]
        elif op == "replace":
            differences += _replaced(theirs[i1:i2], ours[j1:j2], i1)
    return _moved(differences)


def _replaced(theirs: list[Line], ours: list[Line], at: int) -> list[Difference]:
    """A run of EUR-Lex's lines against a run of ours: walked together while their text agrees, and
    where it doesn't, from the nearest lines where it does again."""
    differences = []
    i = j = 0
    while i < len(theirs) and j < len(ours):
        if grouped := _grouped(theirs, ours, i, j):
            ti, oj = grouped
            if ti - i == 1 and oj - j == 1:
                kind = "spacing"
                differences += _set(theirs[i:ti], ours[j:oj], at + i)
            else:
                kind = "split" if ti - i == 1 else "merged" if oj - j == 1 else "regrouped"
            differences.append(Difference(kind, tuple(theirs[i:ti]), tuple(ours[j:oj]), at + i + 1))
            i, j = ti, oj
            continue
        a, b = _resync(theirs, ours, i, j)
        for n in range(max(a, b)):
            t = theirs[i + n] if n < a else None
            o = ours[j + n] if n < b else None
            if t and o:
                kind = "marker" if t.key.endswith(o.key) or o.key.endswith(t.key) else "changed"
                differences.append(Difference(kind, (t,), (o,), at + i + n + 1))
            elif t:
                differences.append(Difference("missing", (t,), (), at + i + n + 1))
            elif o:
                differences.append(Difference("extra", (), (o,), at + i + a + 1))
        i, j = i + a, j + b
    differences += [
        Difference("missing", (t,), (), at + i + n + 1) for n, t in enumerate(theirs[i:])
    ]
    differences += [Difference("extra", (), (o,), at + len(theirs) + 1) for o in ours[j:]]
    return differences


def _set(theirs: list[Line], ours: list[Line], at: int) -> list[Difference]:
    """Lines of the same text, one to one, set differently: across the column, or in another face."""
    differences = []
    for n, (t, o) in enumerate(zip(theirs, ours, strict=True)):
        if not (t.align and o.align):
            continue
        if t.align != o.align:
            detail = f"EUR-Lex {t.align}, ours {o.align}"
            differences.append(Difference("aligned", (t,), (o,), at + n + 1, detail))
        elif t.align == "left" and abs(t.indent - o.indent) > INDENT:
            detail = f"EUR-Lex {t.indent:.0f}px in, ours {o.indent:.0f}px"
            differences.append(Difference("aligned", (t,), (o,), at + n + 1, detail))
        if t.face != o.face:
            detail = f"EUR-Lex {t.face or 'plain'}, ours {o.face or 'plain'}"
            differences.append(Difference("styled", (t,), (o,), at + n + 1, detail))
    return differences


def _grouped(theirs: list[Line], ours: list[Line], i: int, j: int) -> tuple[int, int] | None:
    """The ends of the fewest lines from i (theirs) and j (ours) whose texts are the same, gathered
    on the shorter side until the two meet; none if they don't."""
    t, o = theirs[i].key, ours[j].key
    ti, oj = i + 1, j + 1
    while t != o:
        if t.startswith(o) and oj < len(ours):
            o += ours[oj].key
            oj += 1
        elif o.startswith(t) and ti < len(theirs):
            t += theirs[ti].key
            ti += 1
        else:
            return None
    return ti, oj


def _resync(theirs: list[Line], ours: list[Line], i: int, j: int) -> tuple[int, int]:
    """How many lines to pass over on each side, the fewest in all, to where the texts agree again
    (within RESYNC lines); all that are left if they don't."""
    best = None
    for a in range(min(RESYNC, len(theirs) - i)):
        for b in range(min(RESYNC, len(ours) - j)):
            closer = (a or b) and (best is None or a + b < sum(best))
            if closer and _grouped(theirs, ours, i + a, j + b):
                best = (a, b)
    return best or (len(theirs) - i, len(ours) - j)


def _moved(differences: list[Difference]) -> list[Difference]:
    """A line missing in one place and extra in another is the same line, moved."""
    extra = defaultdict(list)
    for n, d in enumerate(differences):
        if d.kind == "extra":
            extra[d.ours[0].key].append(n)
    moved = {}
    for n, d in enumerate(differences):
        if d.kind == "missing" and extra.get(d.theirs[0].key):
            moved[n] = extra[d.theirs[0].key].pop(0)
    taken = set(moved.values())
    return [
        Difference("moved", d.theirs, differences[moved[n]].ours, d.at) if n in moved else d
        for n, d in enumerate(differences)
        if n not in taken
    ]


def _text(lines: tuple[Line, ...], width: int = 70) -> str:
    text = " ⏎ ".join(line.text for line in lines)
    return text if len(text) <= width else text[: width - 1] + "…"


def report(
    page: str, celex: str, theirs: list[Line], ours: list[Line], differences: list[Difference]
) -> None:
    patterns = defaultdict(list)
    for d in differences:
        patterns[d.pattern].append(d)
    ranked = sorted(patterns.items(), key=lambda p: -len(p[1]))
    kinds = Counter(d.kind for d in differences)
    print(
        f"\n{celex}: EUR-Lex {len(theirs)} lines, ours {len(ours)}; {len(differences)} differences in {len(ranked)} patterns"
    )
    print("  " + ", ".join(f"{kind} {n}" for kind, n in kinds.most_common()))
    for (kind, made_theirs, made_ours), ds in ranked:
        d = ds[0]
        print(
            f"\n  {len(ds):>5}  {kind:<9} EUR-Lex: {made_theirs or '—'}  ours: {made_ours or '—'}"
        )
        if d.theirs:
            print(f"         EUR-Lex (line {d.at}): {_text(d.theirs)}")
        if d.ours:
            print(f"         ours: {_text(d.ours)}")
        if d.detail:
            print(f"         {d.detail}")

    path = OUT / "reports" / f"{celex}.md"
    path.parent.mkdir(parents=True, exist_ok=True)
    with path.open("w", encoding="utf-8") as out:
        out.write(f"# {celex}\n\nOurs: {page}  \nEUR-Lex: {CELLAR}{celex}\n\n")
        out.write(
            f"EUR-Lex {len(theirs)} lines, ours {len(ours)}; {len(differences)} differences\n"
        )
        for (kind, made_theirs, made_ours), ds in ranked:
            out.write(
                f"\n## {len(ds)} {kind}: EUR-Lex {made_theirs or '—'}, ours {made_ours or '—'}\n\n"
            )
            for d in ds:
                out.write(f"- line {d.at}" + (f": {d.detail}" if d.detail else "") + "\n")
                for line in d.theirs:
                    out.write(f"  - EUR-Lex: `{line.text}`\n")
                for line in d.ours:
                    out.write(f"  - ours: `{line.text}`\n")
    print(f"\n  every difference: {path.relative_to(ROOT)}")


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__.splitlines()[0])
    parser.add_argument(
        "pages", nargs="+", help="a version's page: http://127.0.0.1:8080/eli/…/{date}/{language}"
    )
    args = parser.parse_args()

    subprocess.run(
        [sys.executable, "-m", "playwright", "install", "--only-shell", "chromium"], check=True
    )
    with sync_playwright() as playwright:
        browser = playwright.chromium.launch()
        for page in args.pages:
            language = urlparse(page).path.rstrip("/").rsplit("/", 1)[-1]
            print(f"{page}", file=sys.stderr)
            celex, ours = our_page(browser, page)
            html = reference(celex, language)
            if html is None:
                print(
                    f"\n{celex}: Cellar has no xhtml of it in {language}: nothing to compare with"
                )
                continue
            theirs = their_page(browser, html)
            report(page, celex, theirs, ours, compare(theirs, ours))
        browser.close()


if __name__ == "__main__":
    main()
