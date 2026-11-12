// SPDX-License-Identifier: MIT
// @ts-check
import { afterEach, describe, expect, test } from "vitest";
import { coming, pageOf, start, stop } from "../../main/resources/static/js/loading.js";

afterEach(() => {
  document.body.innerHTML = "";
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
