#!/usr/bin/env -S uv run --script --quiet
# SPDX-License-Identifier: MIT
# MISE description="Save each of the viewer's pages, as a reader sees them, as static HTML for a design review"
# /// script
# requires-python = ">=3.14"
# dependencies = ["playwright==1.64.0"]
# ///
"""The viewer's pages, rendered from real data, saved as static HTML for a design review.

    mise run ui:snapshots                       # the viewer at http://127.0.0.1:8081
    mise run ui:snapshots -- --base http://127.0.0.1:8080

Each page (PAGES) is opened in a headless Chromium at a desktop width, in the light theme and in the
dark one (the theme cookie), and left to load what htmx loads into it (its changes, its acts). Then
it is saved as it stands: its stylesheet inlined, its scripts and htmx's attributes removed, so the
file opens anywhere, without the viewer, and shows what the reader saw. Beside each, a full-page PNG.
ui-snapshots/ (ignored by git) gets them and index.json, which lists each page, its address and what
it shows. The pages are the review scenarios of docs/design.md, and Home before a search. Their
acts are 2015/1998, 2018/1862, 767/2008 and 2016/679; the first run prepares them, which takes a
while. Preparing is 185/2010, caught while it is prepared, so it shows only on the first run.
"""

import argparse
import json
import subprocess
import sys
from dataclasses import dataclass
from pathlib import Path
from urllib.parse import urlsplit

from playwright.sync_api import Error, Page, TimeoutError, sync_playwright

ROOT = Path(__file__).resolve().parents[3]
OUT = ROOT / "ui-snapshots"
WIDTH = 1440
HEIGHT = 900
THEMES = ("light", "dark")

AVSEC = "/eli/reg_impl/2015/1998"
SIS = "/eli/reg/2018/1862"
VIS = "/eli/reg/2008/767"
GDPR = "/eli/reg/2016/679"
STEP = AVSEC + "/eng/compare?from=2021-07-01&to=2021-12-31"


@dataclass(frozen=True)
class Shown:
    """A page in one of its review scenarios.

    name: the file's name; page: which page it is, as the design names it; scenario: what it shows;
    path: its address; settled: whether it finishes loading (a page being prepared streams until
    the act is ready, so it is saved as soon as it shows); click: what to click once the page has
    loaded, for a scenario that the reader opens (a CSS selector, or None).
    """

    name: str
    page: str
    scenario: str
    path: str
    settled: bool = True
    click: str | None = None


PAGES = (
    Shown("home", "Home", "Before a search", "/"),
    Shown("home-results", "Home", "A search's results", "/?q=aviation+security"),
    Shown(
        "history",
        "History",
        "An act that applies from more than one day, and a day that the data does not give",
        SIS + "/eng/history?on=2022-08-01",
    ),
    Shown(
        "history-two-days",
        "History",
        "Two days",
        AVSEC + "/eng/history?from=2020-07-02&to=2021-03-15",
    ),
    Shown(
        "amending",
        "The page of an amending act",
        "An act that applies from more than one day, two days",
        SIS + "/eng/history/32022R1190?on=2022-08-01",
    ),
    Shown("read-unchanged", "Read", "An act that no act changed opens on Read", GDPR + "/eng"),
    Shown("read", "Read", "The law on a day", AVSEC + "/2021-12-31/eng"),
    Shown(
        "read-pending",
        "Read",
        "A pending amendment: an act applies that the text does not have, with divergence notes",
        AVSEC + "/2017-06-03/eng",
    ),
    Shown(
        "read-not-applying", "Read", "The act itself does not apply yet", GDPR + "/2017-01-01/eng"
    ),
    Shown(
        "read-prospective",
        "Read",
        "Prospective amendments: provisions that do not apply yet",
        VIS + "/2018-01-01/eng",
    ),
    Shown("read-undated", "Read", "An undated pending amendment", SIS + "/2026-11-17/eng"),
    Shown(
        "read-provision",
        "Read",
        "The history of a provision: Article 6(2)",
        VIS + "/2026-11-17/eng#art_6",
        click='[id="art_6/par_2"] text-tag',
    ),
    Shown("read-missing", "Read", "No text in the language", AVSEC + "/2021-12-31/gle"),
    Shown("compare-changes", "Compare", "Changes", STEP),
    Shown("compare-whole", "Compare", "Whole text", STEP + "&view=whole"),
    Shown("compare-synopsis", "Compare", "Synopsis", STEP + "&view=synopsis"),
    Shown(
        "compare-late",
        "Compare",
        "A day on which an act applies that the text of the day does not have",
        AVSEC + "/eng/compare?from=2016-02-29&to=2017-06-03",
    ),
    Shown(
        "compare-pending",
        "Compare",
        "Changes that do not apply on the later day",
        SIS + "/eng/compare?from=2021-08-03&to=2022-08-01",
    ),
    Shown(
        "compare-provision",
        "Compare",
        "One provision: the wording on the two days and the steps between them",
        VIS + "/eng/compare?from=2017-12-29&to=2026-06-12&occurrence=art_6%2Fpar_2",
    ),
    Shown(
        "preparing", "Preparing", "An act being prepared", "/eli/reg/2010/185/eng", settled=False
    ),
)

# what is removed before a page is saved: its scripts, and htmx's attributes (hx-*, and
# hx-sse:connect's kind), which would ask a server that isn't there
STATIC = """() => {
  for (const script of document.querySelectorAll('script, noscript')) script.remove();
  for (const element of document.querySelectorAll('*')) {
    for (const name of [...element.getAttributeNames()]) {
      if (name.startsWith('hx-') || name.startsWith('sse-')) element.removeAttribute(name);
    }
  }
}"""

# each stylesheet's rules, in place of its link
INLINE = """async () => {
  for (const link of document.querySelectorAll('link[rel=stylesheet]')) {
    const css = await (await fetch(link.href)).text();
    const style = document.createElement('style');
    style.textContent = css;
    link.replaceWith(style);
  }
}"""


def opened(page: Page, base: str, shown: Shown) -> str:
    """Opens {shown}, and what it clicks once the page has loaded; its address."""
    url = base + shown.path
    page.goto(url, wait_until="domcontentloaded")
    if shown.settled:
        settle(page)
    else:
        page.wait_for_timeout(1500)
    if shown.click:
        page.locator(shown.click).first.click()
        settle(page)
    return url


# whether htmx still loads a part of the page. A page of an act keeps a stream of notices open
# (hx-sse:connect), so the network is never idle: the stream does not count.
LOADING = """() => document.readyState === 'complete' && ![...document.querySelectorAll('.htmx-request')]
  .some((element) => !element.hasAttribute('hx-sse:connect'))"""


def settle(page: Page) -> None:
    """Waits until what htmx loads has loaded: no part asked for, twice over half a second."""
    try:
        for _ in range(2):
            page.wait_for_timeout(500)
            page.wait_for_function(LOADING, timeout=60_000)
    except TimeoutError:
        print(f"  {page.url} still loading after a minute; saved as it stands", file=sys.stderr)


def save(page: Page, path: Path) -> None:
    page.screenshot(path=path.with_suffix(".png"), full_page=True)
    page.evaluate(INLINE)
    page.evaluate(STATIC)
    path.write_text("<!DOCTYPE html>\n" + page.evaluate("document.documentElement.outerHTML"))


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__.splitlines()[0])
    parser.add_argument("--base", default="http://127.0.0.1:8081", help="the running viewer")
    base = parser.parse_args().base.rstrip("/")
    host = urlsplit(base).hostname or "127.0.0.1"
    subprocess.run(
        [sys.executable, "-m", "playwright", "install", "--only-shell", "chromium"], check=True
    )
    OUT.mkdir(exist_ok=True)
    index = []
    with sync_playwright() as playwright:
        browser = playwright.chromium.launch()
        for theme in THEMES:
            context = browser.new_context(viewport={"width": WIDTH, "height": HEIGHT})
            context.add_cookies([{"name": "theme", "value": theme, "domain": host, "path": "/"}])
            page = context.new_page()
            for shown in PAGES:
                file = OUT / f"{shown.name}-{theme}.html"
                try:
                    url = opened(page, base, shown)
                    save(page, file)
                except Error as e:
                    print(f"  {shown.name} ({theme}): {e.message.splitlines()[0]}", file=sys.stderr)
                    continue
                print(f"{file.relative_to(ROOT)}  {url}")
                index.append(
                    {
                        "file": file.name,
                        "image": file.with_suffix(".png").name,
                        "page": shown.page,
                        "scenario": shown.scenario,
                        "theme": theme,
                        "url": url,
                        "title": page.title(),
                    }
                )
            context.close()
        browser.close()
    (OUT / "index.json").write_text(json.dumps(index, indent=2, ensure_ascii=False) + "\n")
    print(f"{len(index)} pages in {OUT.relative_to(ROOT)}/")


if __name__ == "__main__":
    main()
