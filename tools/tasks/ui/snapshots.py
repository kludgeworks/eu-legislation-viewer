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
it shows. The pages' addresses are 2015/1998's, an act the viewer has prepared; Preparing is a
second act, 185/2010, caught while it is prepared (the first run prepares it: it takes a while).
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
STEP = AVSEC + "/eng/compare?from=2021-07-01&to=2021-12-31"


@dataclass(frozen=True)
class Shown:
    """A page in one of its states.

    name: the file's name; page: which page it is, as the design names it; state: what it shows;
    path: its address; settled: whether it finishes loading (a page being prepared streams until
    the act is ready, so it is saved as soon as it shows); chip: open the first act chip's address
    instead (an act filter, whichever acts the step has).
    """

    name: str
    page: str
    state: str
    path: str
    settled: bool = True
    chip: bool = False


PAGES = (
    Shown("home", "Home", "Before a search", "/"),
    Shown("home-results", "Home", "A search's results", "/?q=aviation+security"),
    Shown("read", "Read", "A version", AVSEC + "/2021-12-31/eng"),
    Shown("read-missing", "Read", "A version not in the language", AVSEC + "/2021-12-31/gle"),
    Shown("compare-changes", "Compare", "Changes", STEP),
    Shown("compare-whole", "Compare", "Whole text", STEP + "&view=whole"),
    Shown("compare-synopsis", "Compare", "Synopsis", STEP + "&view=synopsis"),
    Shown("compare-act", "Compare", "Changes, one act's", STEP, chip=True),
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
    """Opens {shown}, its act chip's address for an act filter; its address."""
    url = base + shown.path
    page.goto(url, wait_until="domcontentloaded")
    if shown.chip:
        settle(page)
        chip = page.locator(".between a.chip").first
        href = chip.get_attribute("href")
        if href is None:
            raise SystemExit(f"{url} has no act chip to filter by: is the act prepared?")
        url = base + href
        page.goto(url, wait_until="domcontentloaded")
    if shown.settled:
        settle(page)
    else:
        page.wait_for_timeout(1500)
    return url


def settle(page: Page) -> None:
    """Waits until what htmx loads has loaded: nothing more asked for a while."""
    try:
        page.wait_for_load_state("networkidle", timeout=60_000)
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
                        "state": shown.state,
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
