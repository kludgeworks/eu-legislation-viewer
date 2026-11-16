// SPDX-License-Identifier: MIT
// @ts-check
import { afterEach, describe, expect, test } from "vitest";
import { mark } from "../../main/resources/static/js/margin.js";

afterEach(() => {
  document.body.innerHTML = "";
});

const TEXT = `
  <div class="content">
    <div class="unit" id="anx/pnt_4.1.3"><div class="title">4.1.3 Screening of liquids</div>
      <div class="unit" id="anx/pnt_4.1.3.3">4.1.3.3 The screening of LAGs</div>
    </div>
    <div class="unit" id="anx/pnt_4.2">4.2 Protection of passengers</div>
  </div>`;

/**
 * Returns the list of marks and the text of a page.
 *
 * @param {string} rules
 * @returns {{ rules: HTMLElement, content: HTMLElement }}
 */
const page = (rules) => {
  document.body.innerHTML = `<margin-rules hidden>${rules}</margin-rules>${TEXT}`;
  return {
    rules: /** @type {HTMLElement} */ (document.querySelector("margin-rules")),
    content: /** @type {HTMLElement} */ (document.querySelector(".content")),
  };
};

describe("mark", () => {
  test("marks each unit, with a link up to the warning", () => {
    const { rules, content } = page(
      `<span data-unit="anx/pnt_4.1.3.3" data-heading="false" data-label="2017/815 not included"></span>`,
    );

    mark(rules, content);

    const unit = /** @type {HTMLElement} */ (document.getElementById("anx/pnt_4.1.3.3"));
    expect(unit.classList.contains("divergent")).toBe(true);
    const link = /** @type {HTMLAnchorElement} */ (unit.querySelector("a.divergent-link"));
    expect(link.getAttribute("href")).toBe("#divergences");
    expect(link.textContent).toBe("2017/815 not included ↑");
  });

  test("links only the first unit of a run of units with the same words", () => {
    const { rules, content } = page(`
      <span data-unit="anx/pnt_4.1.3" data-heading="false" data-label="2017/815 not included"></span>
      <span data-unit="anx/pnt_4.2" data-heading="false" data-label="2017/815 not included"></span>`);

    mark(rules, content);

    expect(content.querySelectorAll(".divergent")).toHaveLength(2);
    expect(content.querySelectorAll(".divergent-link")).toHaveLength(1);
  });

  test("links a run that starts at a marked heading once", () => {
    document.body.innerHTML = `<margin-rules hidden>
      <span data-unit="anx/pnt_4.1.3" data-heading="true" data-label="2017/815 not included"></span>
      <span data-unit="anx/pnt_4.1.3.1" data-heading="false" data-label="2017/815 not included"></span>
    </margin-rules><div class="content">
      <div class="unit" id="anx/pnt_4.1.3"><h2 class="title">4.1.3 Screening of liquids</h2></div>
      <div class="unit" id="anx/pnt_4.1.3.1">4.1.3.1 LAGs carried by passengers</div>
    </div>`;
    const rules = /** @type {HTMLElement} */ (document.querySelector("margin-rules"));
    const content = /** @type {HTMLElement} */ (document.querySelector(".content"));

    mark(rules, content);

    expect(content.querySelectorAll(".divergent")).toHaveLength(2);
    expect(content.querySelectorAll(".divergent-link")).toHaveLength(1);
  });

  test("marks only the heading of a unit that holds a place the text does not have", () => {
    const { rules, content } = page(
      `<span data-unit="anx/pnt_4.1.3" data-heading="true" data-label="2017/815 not included"></span>`,
    );

    mark(rules, content);

    const unit = /** @type {HTMLElement} */ (document.getElementById("anx/pnt_4.1.3"));
    expect(unit.classList.contains("divergent")).toBe(false);
    expect(unit.querySelector(":scope > .title")?.classList.contains("divergent")).toBe(true);
  });

  test("does not mark a unit in a marked unit, nor a unit the text does not have", () => {
    const { rules, content } = page(`
      <span data-unit="anx/pnt_4.1.3" data-heading="false" data-label="2017/815 not included"></span>
      <span data-unit="anx/pnt_4.1.3.3" data-heading="false" data-label="2017/815 not included"></span>
      <span data-unit="anx/pnt_9" data-heading="false" data-label="2017/815 not included"></span>`);

    mark(rules, content);

    expect(content.querySelectorAll(".divergent")).toHaveLength(1);
  });

  test("puts the note at the top of a text that does not apply at all, once", () => {
    const { rules, content } = page(
      `<p class="margin-note"><b>Does not apply on this day.</b> Applies from 25 May 2018.</p>`,
    );

    mark(rules, content);
    mark(rules, content);

    expect(content.classList.contains("divergent-whole")).toBe(true);
    expect(content.querySelectorAll(".margin-note")).toHaveLength(1);
    expect(content.firstElementChild?.classList.contains("margin-note")).toBe(true);
  });

  test("waits for the text", () => {
    document.body.innerHTML = `<margin-rules hidden><span data-unit="anx" data-label="x"></span></margin-rules><div class="content"><p class="status">Loading the text…</p></div>`;
    const rules = /** @type {HTMLElement} */ (document.querySelector("margin-rules"));
    const content = /** @type {HTMLElement} */ (document.querySelector(".content"));

    mark(rules, content);

    expect(content.querySelector(".divergent")).toBeNull();
  });
});

describe("MarginRules", () => {
  test("marks the text when htmx swaps it in", () => {
    document.body.innerHTML = `<div id="text"><margin-rules hidden><span data-unit="anx/pnt_4.2" data-heading="false" data-label="2017/815 not included"></span></margin-rules><div class="content"><p class="status">Loading the text…</p></div></div>`;
    const content = /** @type {HTMLElement} */ (document.querySelector(".content"));
    expect(content.querySelector(".divergent")).toBeNull();

    content.innerHTML = TEXT;
    content.dispatchEvent(new Event("htmx:after:swap", { bubbles: true }));

    expect(document.getElementById("anx/pnt_4.2")?.classList.contains("divergent")).toBe(true);
  });
});
