// SPDX-License-Identifier: MIT
// @ts-check
import { afterEach, describe, expect, test, vi } from "vitest";
import { arrive, coming, land, pageOf, start, stop } from "../../main/resources/static/js/loading.js";

afterEach(() => {
  document.body.innerHTML = "";
  vi.unstubAllGlobals();
});

const PAGE = `
  <div id="page" data-view="changes">
    <template id="skeleton-synopsis"><div class="skeleton-over synopsis"></div></template>
    <template id="skeleton-changes"><div class="skeleton-over changes"></div></template>
    <p class="page-status visually-hidden" role="status"></p>
    <nav><a id="synopsis" href="/x?view=synopsis" data-view="synopsis">Synopsis</a><a id="dot" href="/y">dot</a></nav>
    <div id="text"><p>The text</p></div>
  </div>`;

/** @param {string} id */
const element = (id) => /** @type {HTMLElement} */ (document.getElementById(id));

describe("loading", () => {
  test("opens the view of the link, or of the page if the link has none", () => {
    document.body.innerHTML = PAGE;
    expect(coming(element("synopsis"))).toBe("synopsis");
    expect(coming(element("dot"))).toBe("changes");
  });

  test("shows the shape of the view that is coming, and puts the page back after", () => {
    document.body.innerHTML = PAGE;
    const page = element("page");
    const link = element("synopsis");

    start(page, link);

    expect(page.classList.contains("loading")).toBe(true);
    expect(link.classList.contains("pressed")).toBe(true);
    expect(page.querySelectorAll("#text > .skeleton-over.synopsis")).toHaveLength(1);
    expect(element("text").getAttribute("aria-busy")).toBe("true");
    expect(page.querySelector(".page-status")?.textContent).toBe("Loading…");

    stop(page);

    expect(page.classList.contains("loading")).toBe(false);
    expect(link.classList.contains("pressed")).toBe(false);
    expect(page.querySelectorAll(".skeleton-over")).toHaveLength(0);
    expect(element("text").hasAttribute("aria-busy")).toBe(false);
    expect(page.querySelector(".page-status")?.textContent).toBe("");
  });

  test("leaves a slot's own request alone", () => {
    document.body.innerHTML = PAGE;
    /** @param {boolean} boosted */
    const event = (boosted) =>
      new CustomEvent("htmx:before:request", {
        detail: {
          ctx: {
            sourceElement: element("dot"),
            request: { headers: boosted ? { "HX-Boosted": "true" } : {} },
          },
        },
      });
    expect(pageOf(event(true))).toBe(element("page"));
    expect(pageOf(event(false))).toBeNull();
  });
});

describe("a page opened at a fragment", () => {
  test("scrolls to the fragment's element once a swap has brought it", () => {
    location.hash = "#anx%2Fpnt_8.1.2.3%2Fpnt_c";
    expect(arrive()).toBe(false);

    document.body.innerHTML = `<section id="anx/pnt_8.1.2.3/pnt_c"></section>`;
    const target = /** @type {HTMLElement} */ (document.getElementById("anx/pnt_8.1.2.3/pnt_c"));
    const scrolled = vi.fn();
    target.scrollIntoView = scrolled;

    expect(arrive()).toBe(true);
    expect(scrolled).toHaveBeenCalledOnce();
    location.hash = "";
  });
});

/**
 * Stubs the frames of the browser. Returns a function that runs the frames asked for so far, and
 * returns how many there were.
 */
const frames = () => {
  /** @type {FrameRequestCallback[]} */
  let asked = [];
  vi.stubGlobal("requestAnimationFrame", (/** @type {FrameRequestCallback} */ frame) => asked.push(frame));
  return () => {
    const due = asked;
    asked = [];
    due.forEach((frame) => frame(0));
    return due.length;
  };
};

/** @param {number[]} tops where the element is in each frame; it stays at the last */
const moving = (tops) => {
  const target = document.createElement("section");
  const scrolled = vi.fn();
  target.scrollIntoView = scrolled;
  target.getBoundingClientRect = () => /** @type {DOMRect} */ ({ top: tops.length > 1 ? tops.shift() : tops[0] });
  return { target, scrolled };
};

describe("a jump to an element", () => {
  test("scrolls again until the element stays where it is", () => {
    const run = frames();
    const { target, scrolled } = moving([2796, 300, 48]);

    land(target);
    while (run() > 0);

    // the jump, then once for each place the element was at, the last of which it stays at
    expect(scrolled).toHaveBeenCalledTimes(4);
  });

  test("gives up on an element that never stays", () => {
    const run = frames();
    let top = 0;
    const { target, scrolled } = moving([0]);
    target.getBoundingClientRect = () => /** @type {DOMRect} */ ({ top: top++ });

    land(target);
    while (run() > 0);

    expect(scrolled).toHaveBeenCalledTimes(11);
  });
});
