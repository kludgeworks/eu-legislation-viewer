// SPDX-License-Identifier: MIT
// @ts-check
import { afterEach, describe, expect, test, vi } from "vitest";
import { change, number, said } from "../../main/resources/static/js/markers.js";

afterEach(() => {
  document.body.innerHTML = "";
  vi.restoreAllMocks();
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
    expect(number("32019R0103R(01)")).toBe("Corrigendum 1 to 2019/103");
  });

  test("returns anything else as it is", () => {
    expect(number("not a CELEX number")).toBe("not a CELEX number");
    expect(number(undefined)).toBe("");
  });
});

describe("said", () => {
  test("says what happened and which act did it", () => {
    document.body.innerHTML = `<amendment-mark class="provenance" data-act="32017R0815" data-action="replaced">▼</amendment-mark>`;

    expect(said(mark())).toBe("Replaced by 2017/815");
  });

  test("says when the act applies, where the page lists it", () => {
    document.body.innerHTML = `<div class="marks" hidden><span data-act="32026R0449"
      data-applies="applies from 1 October 2026 · Article 2 of 2026/449" data-compare="/step"></span></div>
      <amendment-mark class="provenance" data-act="32026R0449" data-action="replaced">▼</amendment-mark>`;

    expect(said(mark())).toBe(
      "Replaced by 2026/449 · applies from 1 October 2026 · Article 2 of 2026/449",
    );
  });

  test("says amended if the mark does not say what happened", () => {
    document.body.innerHTML = `<amendment-mark class="provenance" data-act="32017R0815">▼</amendment-mark>`;

    expect(said(mark())).toBe("Amended by 2017/815");
  });
});

describe("change", () => {
  test("opens the step that made the change, at the unit of the mark", () => {
    document.body.innerHTML = `
      <div class="marks" hidden><span data-act="32026R0449" data-compare="/compare?from=2026-03-22&amp;to=2026-10-01"></span></div>
      <div data-compare="/eli/reg_impl/2015/1998/eng/compare">
        <div id="anx/pnt_8.1.2.3/pnt_c"><amendment-mark class="provenance" data-act="32026R0449">▼</amendment-mark></div>
      </div>`;

    expect(change(mark())).toBe("/compare?from=2026-03-22&to=2026-10-01#anx%2Fpnt_8.1.2.3%2Fpnt_c");
  });

  test("opens the comparison of the text, limited to the act's changes, if the act is not listed", () => {
    document.body.innerHTML = `
      <div data-compare="/eli/reg_impl/2015/1998/eng/compare">
        <amendment-mark class="provenance" data-act="32019R0103R(01)">▼</amendment-mark>
      </div>`;

    expect(change(mark())).toBe(
      "/eli/reg_impl/2015/1998/eng/compare?act=32019R0103R(01)",
    );
  });

  test("has no change for a mark outside a text with a comparison", () => {
    document.body.innerHTML = `<amendment-mark class="provenance" data-act="32017R0815">▼</amendment-mark>`;

    expect(change(mark())).toBeNull();
  });
});

describe("AmendmentMark", () => {
  test("sets its hover text when connected inside a comparison-enabled text", () => {
    document.body.innerHTML = `
      <div data-compare="/eli/reg_impl/2015/1998/eng/compare">
        <amendment-mark class="provenance" data-act="32017R0815" data-action="inserted">▼</amendment-mark>
      </div>`;

    expect(mark().title).toBe("Inserted by 2017/815");
  });

  test("leaves hover text empty outside a comparison", () => {
    document.body.innerHTML = `
      <div>
        <amendment-mark class="provenance" data-act="32017R0815" data-action="inserted">▼</amendment-mark>
      </div>`;

    expect(mark().title).toBe("");
  });

  test("navigates to the comparison on click", () => {
    const assign = vi.fn();
    vi.stubGlobal("location", { assign });

    document.body.innerHTML = `
      <div data-compare="/eli/reg_impl/2015/1998/eng/compare">
        <amendment-mark class="provenance" data-act="32017R0815" data-action="inserted">▼</amendment-mark>
      </div>`;

    mark().click();

    expect(assign).toHaveBeenCalledWith("/eli/reg_impl/2015/1998/eng/compare?act=32017R0815");
  });

  test("navigates to the comparison on Enter key", () => {
    const assign = vi.fn();
    vi.stubGlobal("location", { assign });

    document.body.innerHTML = `
      <div data-compare="/eli/reg_impl/2015/1998/eng/compare">
        <amendment-mark class="provenance" data-act="32017R0815" data-action="inserted">▼</amendment-mark>
      </div>`;

    mark().dispatchEvent(new KeyboardEvent("keydown", { key: "Enter" }));

    expect(assign).toHaveBeenCalledWith("/eli/reg_impl/2015/1998/eng/compare?act=32017R0815");
  });
});
