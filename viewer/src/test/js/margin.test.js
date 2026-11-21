// SPDX-License-Identifier: MIT
// @ts-check
import { afterEach, describe, expect, test } from "vitest";
import { mark } from "../../main/resources/static/js/margin.js";

afterEach(() => {
  document.body.innerHTML = "";
});

const OUTLINE = `
  <nav class="outline"><ul>
    <li><details><summary><a href="#anx">Annex</a></summary><ul>
      <li><a href="#anx/pnt_4.1.3">4.1.3 Screening of liquids</a></li>
      <li><a href="#anx/pnt_4.2">4.2 Protection of passengers</a></li>
    </ul></details></li>
  </ul></nav>`;

const TEXT = `
  <div class="content"><div class="occurrence" id="anx">
    <div class="occurrence" id="anx/pnt_4.1.3"><div class="title">4.1.3 Screening of liquids</div>
      <div class="occurrence" id="anx/pnt_4.1.3.3">4.1.3.3 The screening of LAGs</div>
    </div>
    <div class="occurrence" id="anx/pnt_4.2">4.2 Protection of passengers</div>
  </div></div>`;

const ACT = "/eli/reg_impl/2015/1998/eng/history/32017R0815";

/**
 * Returns a rule for a change that does not apply on the day, as the page lists it.
 *
 * @param {string} occurrence
 * @param {boolean} [heading]
 * @returns {string}
 */
const rule = (occurrence, heading = false) =>
  `<p class="rule-note" data-occurrence="${occurrence}" data-heading="${heading}"><a href="${ACT}">2017/815</a> · does not apply yet</p>`;

/**
 * Returns a marker for a change that the text does not show, as the page lists it.
 *
 * @param {string} occurrence
 * @param {boolean} end
 * @returns {string}
 */
const marker = (occurrence, end) =>
  `<p class="marker" data-occurrence="${occurrence}" data-end="${end}"><span class="marker-kind">Not in this text</span>: point 4.1.3.4 · <a href="${ACT}">2017/815</a></p>`;

/**
 * Returns the list of marks, the text and the outline of a page.
 *
 * @param {string} rules
 * @returns {{ rules: HTMLElement, content: HTMLElement, outline: HTMLElement }}
 */
const page = (rules) => {
  document.body.innerHTML = `${OUTLINE}<margin-rules hidden>${rules}</margin-rules>${TEXT}`;
  return {
    rules: /** @type {HTMLElement} */ (document.querySelector("margin-rules")),
    content: /** @type {HTMLElement} */ (document.querySelector(".content")),
    outline: /** @type {HTMLElement} */ (document.querySelector("nav.outline")),
  };
};

/**
 * Returns the occurrence with the eId `id`.
 *
 * @param {string} id
 * @returns {HTMLElement}
 */
const occurrence = (id) => /** @type {HTMLElement} */ (document.getElementById(id));

describe("mark", () => {
  test("gives a change that does not apply a rule and a note with the act as a link", () => {
    const { rules, content } = page(rule("anx/pnt_4.1.3.3"));

    mark(rules, content);

    expect(occurrence("anx/pnt_4.1.3.3").classList.contains("not-applying")).toBe(true);
    const note = /** @type {HTMLElement} */ (occurrence("anx/pnt_4.1.3.3").querySelector(".rule-note"));
    expect(note.textContent).toBe("2017/815 · does not apply yet");
    expect(note.querySelector("a")?.getAttribute("href")).toBe(ACT);
    expect(note.dataset.occurrence).toBeUndefined();
  });

  test("gives one note to a run of occurrences with the same words", () => {
    const { rules, content } = page(rule("anx/pnt_4.1.3") + rule("anx/pnt_4.2"));

    mark(rules, content);

    expect(content.querySelectorAll(".not-applying")).toHaveLength(2);
    expect(content.querySelectorAll(".rule-note")).toHaveLength(1);
  });

  test("marks only the heading of an occurrence that holds the place", () => {
    const { rules, content } = page(rule("anx/pnt_4.1.3", true));

    mark(rules, content);

    expect(occurrence("anx/pnt_4.1.3").classList.contains("not-applying")).toBe(false);
    expect(content.querySelector(".title")?.classList.contains("not-applying")).toBe(true);
  });

  test("does not mark an occurrence in an occurrence that has the rule", () => {
    const { rules, content } = page(rule("anx/pnt_4.1.3") + rule("anx/pnt_4.1.3.3"));

    mark(rules, content);

    expect(content.querySelectorAll(".not-applying")).toHaveLength(1);
  });

  test("puts a marker before an occurrence that the text has", () => {
    const { rules, content } = page(marker("anx/pnt_4.2", false));

    mark(rules, content);

    const made = /** @type {HTMLElement} */ (occurrence("anx/pnt_4.2").previousElementSibling);
    expect(made.classList.contains("marker")).toBe(true);
    expect(made.textContent).toBe("Not in this text: point 4.1.3.4 · 2017/815");
    expect(occurrence("anx/pnt_4.2").classList.contains("not-applying")).toBe(false);
  });

  test("puts a marker at the end of the occurrence that holds a place that the text does not have", () => {
    const { rules, content } = page(marker("anx/pnt_4.1.3", true));

    mark(rules, content);

    expect(occurrence("anx/pnt_4.1.3").lastElementChild?.classList.contains("marker")).toBe(true);
  });

  test("gives the outline a dot on the entry of a mark and on the entries that hold it", () => {
    const { rules, content, outline } = page(rule("anx/pnt_4.1.3.3") + marker("anx/pnt_4.2", false));

    mark(rules, content, outline);

    // Point 4.1.3.3 has no entry: the dot is on point 4.1.3, which holds it.
    const liquids = /** @type {HTMLElement} */ (outline.querySelector('a[href="#anx/pnt_4.1.3"]'));
    expect(liquids.classList.contains("has-rule")).toBe(true);
    expect(liquids.textContent).toContain("has a change that does not apply on the day");
    const passengers = /** @type {HTMLElement} */ (outline.querySelector('a[href="#anx/pnt_4.2"]'));
    expect(passengers.classList.contains("has-marker")).toBe(true);
    const annex = /** @type {HTMLElement} */ (outline.querySelector('a[href="#anx"]'));
    expect(annex.classList.contains("has-rule")).toBe(true);
    expect(annex.classList.contains("has-marker")).toBe(true);
  });

  test("does nothing for a text that has its marks, or that is not there yet", () => {
    const { rules, content } = page(rule("anx/pnt_4.2"));

    mark(rules, content);
    mark(rules, content);

    expect(content.querySelectorAll(".rule-note")).toHaveLength(1);
    document.body.innerHTML = `<margin-rules hidden>${rule("anx/pnt_4.2")}</margin-rules><div class="content"><p class="status">Loading</p></div>`;
    const empty = /** @type {HTMLElement} */ (document.querySelector(".content"));
    mark(/** @type {HTMLElement} */ (document.querySelector("margin-rules")), empty);
    expect(empty.querySelector(".not-applying")).toBeNull();
  });
});
