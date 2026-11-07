// SPDX-License-Identifier: MIT
// @ts-check
import { afterEach, describe, expect, test } from "vitest";
import { change, number, said } from "../../main/resources/static/js/markers.js";

afterEach(() => {
  document.body.innerHTML = "";
});

/**
 * Returns the mark of the page.
 *
 * @returns {HTMLElement}
 */
const mark = () => /** @type {HTMLElement} */ (document.querySelector(".provenance"));

describe("number", () => {
  test("names an act by its year and number", () => {
    expect(number("32017R0815")).toBe("2017/815");
    expect(number("32015L0849")).toBe("2015/849");
  });

  test("names a corrigendum with the act it corrects", () => {
    expect(number("32019R0103R(01)")).toBe("corrigendum 1 to 2019/103");
  });

  test("returns anything else as it is", () => {
    expect(number("not a CELEX number")).toBe("not a CELEX number");
    expect(number(undefined)).toBe("");
  });
});

describe("said", () => {
  test("says what happened and which act did it", () => {
    document.body.innerHTML = `<span class="provenance" data-act="32017R0815" data-action="replaced">▼</span>`;

    expect(said(mark())).toBe("Replaced by 2017/815 · open this change in Compare");
  });

  test("says amended if the mark does not say what happened", () => {
    document.body.innerHTML = `<span class="provenance" data-act="32017R0815">▼</span>`;

    expect(said(mark())).toBe("Amended by 2017/815 · open this change in Compare");
  });
});

describe("change", () => {
  test("opens the comparison of the text, limited to the act's changes", () => {
    document.body.innerHTML = `
      <div data-compare="/eli/reg_impl/2015/1998/eng/compare">
        <span class="provenance" data-act="32019R0103R(01)">▼</span>
      </div>`;

    expect(change(mark())).toBe(
      "/eli/reg_impl/2015/1998/eng/compare?act=32019R0103R(01)",
    );
  });

  test("has no change for a mark outside a text with a comparison", () => {
    document.body.innerHTML = `<span class="provenance" data-act="32017R0815">▼</span>`;

    expect(change(mark())).toBeNull();
  });
});

describe("after htmx settles", () => {
  test("every mark in a text with a comparison has its hover text", () => {
    document.body.innerHTML = `
      <div data-compare="/eli/reg_impl/2015/1998/eng/compare">
        <span class="provenance" data-act="32017R0815" data-action="inserted">▼</span>
      </div>`;

    document.dispatchEvent(new Event("htmx:after:settle"));

    expect(mark().title).toBe("Inserted by 2017/815 · open this change in Compare");
  });
});
