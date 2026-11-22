// SPDX-License-Identifier: MIT
// @ts-check
import { afterEach, describe, expect, test, vi } from "vitest";
import { history, link, number, said, toggle, occurrenceOf } from "../../main/resources/static/js/tags.js";

afterEach(() => {
  document.body.innerHTML = "";
  vi.restoreAllMocks();
});

/**
 * Returns the tag of the page.
 *
 * @returns {HTMLElement}
 */
const tag = () => /** @type {HTMLElement} */ (document.querySelector(".tag"));

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
    document.body.innerHTML = `<text-tag class="tag" data-act="32017R0815" data-modification="replaced">▼</text-tag>`;

    expect(said(tag())).toBe("Replaced by 2017/815");
  });

  test("says when the act applies, where the page lists it", () => {
    document.body.innerHTML = `<div class="tags" hidden><span data-act="32026R0449"
      data-applies="applies from 1 October 2026 · Article 2 of 2026/449"></span></div>
      <text-tag class="tag" data-act="32026R0449" data-modification="replaced">▼</text-tag>`;

    expect(said(tag())).toBe(
      "Replaced by 2026/449 · applies from 1 October 2026 · Article 2 of 2026/449",
    );
  });

  test("says amended if the tag does not say what happened", () => {
    document.body.innerHTML = `<text-tag class="tag" data-act="32017R0815">▼</text-tag>`;

    expect(said(tag())).toBe("Amended by 2017/815");
  });
});

/**
 * Puts a text with one tagged paragraph and one paragraph without a tag on the page, with the
 * address of the histories.
 *
 * @param {string} [listed] the eIds that the page lists as having a history
 */
const text = (listed = "") => {
  document.body.innerHTML = `
    <div id="text">
      <provision-links hidden data-href="/eli/reg/2008/767/2026-11-17/eng/provision">${listed
        .split(" ")
        .filter(Boolean)
        .map((occurrence) => `<span data-occurrence="${occurrence}"></span>`)
        .join("")}</provision-links>
      <div class="content">
        <div id="art_6"><h3 class="title">Article 6</h3>
          <div id="art_6/par_1"><span class="num">1.</span><p>One.</p></div>
          <div id="art_6/par_2"><text-tag class="tag" data-act="32024R1356" data-modification="replaced"></text-tag><span class="num">2.</span><p>Two.</p></div>
        </div>
      </div>
    </div>`;
};

/**
 * Makes `fetch` answer with `body`.
 *
 * @param {string} body
 * @param {boolean} [ok]
 * @returns {import("vitest").Mock}
 */
const answer = (body, ok = true) => {
  const fetched = vi.fn(async () => ({ ok, status: ok ? 200 : 404, text: async () => body }));
  vi.stubGlobal("fetch", fetched);
  return fetched;
};

const occurrence = (/** @type {string} */ id) =>
  /** @type {HTMLElement} */ (document.getElementById(id));

describe("history", () => {
  test("is the address of the histories with the occurrence", () => {
    text();

    expect(history(occurrence("art_6/par_2"))).toBe(
      "/eli/reg/2008/767/2026-11-17/eng/provision?occurrence=art_6%2Fpar_2",
    );
  });

  test("is null on a page without the address of the histories", () => {
    document.body.innerHTML = `<div id="art_6"></div>`;

    expect(history(occurrence("art_6"))).toBeNull();
  });
});

describe("toggle", () => {
  test("loads the history under the provision, and closes it again", async () => {
    text();
    const fetched = answer(`<aside class="provision-history"><p>History of Article 6(2)</p></aside>`);
    const from = tag();

    await toggle(occurrence("art_6/par_2"), from);

    expect(fetched).toHaveBeenCalledWith(
      "/eli/reg/2008/767/2026-11-17/eng/provision?occurrence=art_6%2Fpar_2",
      expect.anything(),
    );
    expect(occurrence("art_6/par_2").nextElementSibling?.textContent).toBe("History of Article 6(2)");
    expect(from.getAttribute("aria-expanded")).toBe("true");

    await toggle(occurrence("art_6/par_2"), from);

    expect(document.querySelector(".provision-history")).toBeNull();
    expect(from.getAttribute("aria-expanded")).toBe("false");
  });

  test("puts the history of an article after its heading", async () => {
    text();
    answer(`<aside class="provision-history">History</aside>`);

    await toggle(occurrence("art_6"), tag());

    expect(occurrence("art_6").querySelector(".title")?.nextElementSibling?.className).toBe(
      "provision-history",
    );
  });

  test("keeps the history of a paragraph open when the history of its article opens", async () => {
    text();
    answer(`<aside class="provision-history">History</aside>`);

    await toggle(occurrence("art_6/par_2"), tag());
    await toggle(occurrence("art_6"), tag());

    expect(document.querySelectorAll(".provision-history")).toHaveLength(2);

    await toggle(occurrence("art_6"), tag());

    expect(document.querySelectorAll(".provision-history")).toHaveLength(1);
    expect(occurrence("art_6/par_2").nextElementSibling?.className).toBe("provision-history");
  });

  test("says so if the history does not load", async () => {
    text();
    answer("", false);

    await toggle(occurrence("art_6/par_2"), tag());

    expect(document.querySelector(".provision-history")?.textContent).toBe(
      "The history of this provision did not load. Try again.",
    );
  });
});

const links = () => /** @type {HTMLElement} */ (document.querySelector("provision-links"));
const content = () => /** @type {HTMLElement} */ (document.querySelector(".content"));

describe("link", () => {
  test("gives a listed provision without a tag a link that opens its history", async () => {
    text("art_6/par_1");
    const fetched = answer(`<aside class="provision-history">History</aside>`);

    link(links(), content());
    const made = /** @type {HTMLElement} */ (occurrence("art_6/par_1").querySelector(".provision-link"));
    made.click();
    await vi.waitFor(() => expect(made.getAttribute("aria-expanded")).toBe("true"));

    expect(made.textContent).toBe("▽");
    expect(made.getAttribute("aria-label")).toBe("History of this provision");
    expect(fetched).toHaveBeenCalledWith(
      "/eli/reg/2008/767/2026-11-17/eng/provision?occurrence=art_6%2Fpar_1",
      expect.anything(),
    );
  });

  test("gives no link to a provision that has a tag of its own", () => {
    text("art_6/par_2");

    link(links(), content());

    expect(document.querySelector(".provision-link")).toBeNull();
  });

  test("gives a link to a provision whose tags are all on its occurrences", () => {
    text("art_6");

    link(links(), content());

    expect(occurrence("art_6").querySelector(":scope > .provision-link")).not.toBeNull();
  });

  test("does not link a text two times", () => {
    text("art_6/par_1");

    link(links(), content());
    link(links(), content());

    expect(document.querySelectorAll(".provision-link")).toHaveLength(1);
  });
});

describe("TextTag", () => {
  test("sets its hover text when connected inside the text of Read", () => {
    text();

    expect(tag().title).toBe("Replaced by 2024/1356");
  });

  test("leaves hover text empty outside the text of Read", () => {
    document.body.innerHTML = `
      <div>
        <text-tag class="tag" data-act="32017R0815" data-modification="inserted">▼</text-tag>
      </div>`;

    expect(tag().title).toBe("");
  });

  test("opens the history of its provision on click", async () => {
    text();
    const fetched = answer(`<aside class="provision-history">History</aside>`);

    tag().click();
    await vi.waitFor(() => expect(tag().getAttribute("aria-expanded")).toBe("true"));

    expect(occurrenceOf(tag())?.id).toBe("art_6/par_2");
    expect(fetched).toHaveBeenCalledTimes(1);
  });

  test("opens the history of its provision on Enter", async () => {
    text();
    const fetched = answer(`<aside class="provision-history">History</aside>`);

    tag().dispatchEvent(new KeyboardEvent("keydown", { key: "Enter" }));
    await vi.waitFor(() => expect(fetched).toHaveBeenCalledTimes(1));
  });
});
