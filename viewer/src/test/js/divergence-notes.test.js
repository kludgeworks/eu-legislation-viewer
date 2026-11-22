// SPDX-License-Identifier: MIT
// @ts-check
import { afterEach, describe, expect, test } from "vitest";
import { place } from "../../main/resources/static/js/divergence-notes.js";

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
 * Returns the divergence note of a prospective amendment, a change that does not apply on the day, as the
 * page lists it.
 *
 * @param {string} occurrence
 * @param {boolean} [heading]
 * @returns {string}
 */
const prospective = (occurrence, heading = false) =>
  `<p class="prospective-note" data-occurrence="${occurrence}" data-heading="${heading}"><a href="${ACT}">2017/815</a> · does not apply yet</p>`;

/**
 * Returns the divergence note of a pending amendment, a change that the text does not show, as the page lists
 * it.
 *
 * @param {string} occurrence
 * @param {boolean} end
 * @returns {string}
 */
const pending = (occurrence, end) =>
  `<p class="pending-note" data-occurrence="${occurrence}" data-end="${end}"><span class="pending-note-kind">Not in this text</span>: point 4.1.3.4 · <a href="${ACT}">2017/815</a></p>`;

/**
 * Returns the list of divergence notes, the text and the outline of a page.
 *
 * @param {string} listed
 * @returns {{ divergenceNotes: HTMLElement, content: HTMLElement, outline: HTMLElement }}
 */
const page = (listed) => {
  document.body.innerHTML = `${OUTLINE}<divergence-notes hidden>${listed}</divergence-notes>${TEXT}`;
  return {
    divergenceNotes: /** @type {HTMLElement} */ (document.querySelector("divergence-notes")),
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

describe("place", () => {
  test("gives a prospective amendment a bar and a divergence note with the act as a link", () => {
    const { divergenceNotes, content } = page(prospective("anx/pnt_4.1.3.3"));

    place(divergenceNotes, content);

    expect(occurrence("anx/pnt_4.1.3.3").classList.contains("not-applying")).toBe(true);
    const divergenceNote = /** @type {HTMLElement} */ (occurrence("anx/pnt_4.1.3.3").querySelector(".prospective-note"));
    expect(divergenceNote.textContent).toBe("2017/815 · does not apply yet");
    expect(divergenceNote.querySelector("a")?.getAttribute("href")).toBe(ACT);
    expect(divergenceNote.dataset.occurrence).toBeUndefined();
  });

  test("gives one divergence note to a run of occurrences with the same words", () => {
    const { divergenceNotes, content } = page(prospective("anx/pnt_4.1.3") + prospective("anx/pnt_4.2"));

    place(divergenceNotes, content);

    expect(content.querySelectorAll(".not-applying")).toHaveLength(2);
    expect(content.querySelectorAll(".prospective-note")).toHaveLength(1);
  });

  test("gives the bar only to the heading of an occurrence that holds the place", () => {
    const { divergenceNotes, content } = page(prospective("anx/pnt_4.1.3", true));

    place(divergenceNotes, content);

    expect(occurrence("anx/pnt_4.1.3").classList.contains("not-applying")).toBe(false);
    expect(content.querySelector(".title")?.classList.contains("not-applying")).toBe(true);
  });

  test("gives no bar to an occurrence in an occurrence that has the bar", () => {
    const { divergenceNotes, content } = page(prospective("anx/pnt_4.1.3") + prospective("anx/pnt_4.1.3.3"));

    place(divergenceNotes, content);

    expect(content.querySelectorAll(".not-applying")).toHaveLength(1);
  });

  test("puts the divergence note of a pending amendment before an occurrence that the text has", () => {
    const { divergenceNotes, content } = page(pending("anx/pnt_4.2", false));

    place(divergenceNotes, content);

    const made = /** @type {HTMLElement} */ (occurrence("anx/pnt_4.2").previousElementSibling);
    expect(made.classList.contains("pending-note")).toBe(true);
    expect(made.textContent).toBe("Not in this text: point 4.1.3.4 · 2017/815");
    expect(occurrence("anx/pnt_4.2").classList.contains("not-applying")).toBe(false);
  });

  test("puts the divergence note of a pending amendment at the end of the occurrence that holds a place that the text does not have", () => {
    const { divergenceNotes, content } = page(pending("anx/pnt_4.1.3", true));

    place(divergenceNotes, content);

    expect(occurrence("anx/pnt_4.1.3").lastElementChild?.classList.contains("pending-note")).toBe(true);
  });

  test("gives the outline a dot on the entry of a divergence note and on the entries that hold it", () => {
    const { divergenceNotes, content, outline } = page(prospective("anx/pnt_4.1.3.3") + pending("anx/pnt_4.2", false));

    place(divergenceNotes, content, outline);

    // Point 4.1.3.3 has no entry: the dot is on point 4.1.3, which holds it.
    const liquids = /** @type {HTMLElement} */ (outline.querySelector('a[href="#anx/pnt_4.1.3"]'));
    expect(liquids.classList.contains("has-prospective")).toBe(true);
    expect(liquids.textContent).toContain("has a change that does not apply on the day");
    const passengers = /** @type {HTMLElement} */ (outline.querySelector('a[href="#anx/pnt_4.2"]'));
    expect(passengers.classList.contains("has-pending")).toBe(true);
    const annex = /** @type {HTMLElement} */ (outline.querySelector('a[href="#anx"]'));
    expect(annex.classList.contains("has-prospective")).toBe(true);
    expect(annex.classList.contains("has-pending")).toBe(true);
  });

  test("does nothing for a text that has its divergence notes, or that is not there yet", () => {
    const { divergenceNotes, content } = page(prospective("anx/pnt_4.2"));

    place(divergenceNotes, content);
    place(divergenceNotes, content);

    expect(content.querySelectorAll(".prospective-note")).toHaveLength(1);
    document.body.innerHTML = `<divergence-notes hidden>${prospective("anx/pnt_4.2")}</divergence-notes><div class="content"><p class="status">Loading</p></div>`;
    const empty = /** @type {HTMLElement} */ (document.querySelector(".content"));
    place(/** @type {HTMLElement} */ (document.querySelector("divergence-notes")), empty);
    expect(empty.querySelector(".not-applying")).toBeNull();
  });
});
