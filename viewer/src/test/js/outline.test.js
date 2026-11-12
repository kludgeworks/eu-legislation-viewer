// SPDX-License-Identifier: MIT
// @ts-check
import { afterEach, describe, expect, test, vi } from "vitest";
import {
  AT,
  RAIL,
  follow,
  format,
  part,
  passed,
  rail,
  remembered,
} from "../../main/resources/static/js/outline.js";

afterEach(() => {
  document.body.innerHTML = "";
  localStorage.clear();
  vi.restoreAllMocks();
});

/**
 * Returns the one element of the page that matches a selector.
 *
 * @param {string} selector
 * @returns {HTMLElement}
 */
const one = (selector) => /** @type {HTMLElement} */ (document.querySelector(selector));

/** An outline of two parts, Article 1 from difference 1 and Annex IV from difference 3. */
const OUTLINE = `
  <act-outline>
    <nav class="outline counted">
      <button class="outline-rail" aria-pressed="false">⇤</button>
      <div class="outline-follow" data-total="1828">
        <span class="where"></span>
        <a class="up" href="#">↑</a>
        <a class="down" href="#">↓</a>
      </div>
      <ul>
        <li><a class="has" href="#difference-1"><span> Article 1 </span></a></li>
        <li><a class="has" href="#difference-3"><span>Annex IV</span></a></li>
      </ul>
    </nav>
  </act-outline>`;

/**
 * Adds differences 1 to 4 to the page, the first `above` of them above the line the reader is at.
 *
 * @param {number} above
 */
const differences = (above) => {
  for (let n = 1; n <= 4; n++) {
    const difference = document.createElement("section");
    difference.id = `difference-${n}`;
    const top = n <= above ? AT - 10 : AT + 100 * n;
    difference.getBoundingClientRect = () => DOMRect.fromRect({ y: top });
    document.body.append(difference);
  }
};

describe("format", () => {
  test("formats a number in English", () => {
    expect(format(1828)).toBe("1,828");
  });
});

describe("remembered", () => {
  test("remembers a rail", () => {
    expect(remembered({ getItem: () => "true" })).toBe(true);
    expect(remembered({ getItem: () => null })).toBe(false);
  });

  test("is not a rail if the browser does not let the page read its storage", () => {
    const blocked = {
      getItem: () => {
        throw new DOMException("blocked", "SecurityError");
      },
    };
    expect(remembered(blocked)).toBe(false);
  });
});

describe("rail", () => {
  test("collapses the outline to a rail and expands it again", () => {
    document.body.innerHTML = OUTLINE;
    const element = one(".outline");

    rail(element, true);
    expect(element.classList.contains("rail")).toBe(true);
    expect(one(".outline-rail").getAttribute("aria-label")).toBe("Expand the outline");

    rail(element, false);
    expect(element.classList.contains("rail")).toBe(false);
    expect(one(".outline-rail").getAttribute("aria-pressed")).toBe("false");
  });
});

describe("part", () => {
  test("names the last part that starts at or before the difference", () => {
    document.body.innerHTML = OUTLINE;

    expect(part(one(".outline"), 2)).toBe("Article 1");
    expect(part(one(".outline"), 3)).toBe("Annex IV");
    expect(part(one(".outline"), 0)).toBe("");
  });
});

describe("follow", () => {
  test("counts the differences before the reader reaches the first", () => {
    document.body.innerHTML = OUTLINE;
    differences(0);

    follow(document);

    expect(one(".where").textContent).toBe("1,828 differences");
    expect(one(".up").getAttribute("aria-disabled")).toBe("true");
    expect(one(".down").getAttribute("href")).toBe("#difference-1");
  });

  test("says which difference the reader is at, and in which part", () => {
    document.body.innerHTML = OUTLINE;
    differences(3);

    follow(document);

    expect(one(".where").textContent).toBe("Difference 3 of 1,828 · Annex IV");
    expect(one(".up").getAttribute("href")).toBe("#difference-2");
    expect(one(".down").getAttribute("href")).toBe("#difference-4");
    expect(one(".up").hasAttribute("aria-disabled")).toBe(false);
  });
});

describe("passed", () => {
  test("finds the last place above the line the reader is at", () => {
    differences(2);
    const all = [...document.querySelectorAll("[id^='difference-']")];
    expect(passed(all, false)).toBe(1);
    expect(passed([], false)).toBe(-1);
  });

  test("counts every place in the window as passed at the end of the page", () => {
    differences(2);
    const all = [...document.querySelectorAll("[id^='difference-']")];
    // Differences 3 and 4 are at 440 and 540, in a window 768 high.
    expect(passed(all, true)).toBe(3);
  });
});

describe("follow in Compare", () => {
  test("marks the part the reader is in and opens it", () => {
    document.body.innerHTML = OUTLINE;
    differences(4);

    follow(document);

    const marked = document.querySelectorAll("[aria-current='location']");
    expect(marked).toHaveLength(1);
    expect(marked[0]?.getAttribute("href")).toBe("#difference-3");
  });
});

/**
 * Adds the units to the page, those in `above` above the line the reader is at.
 *
 * @param {string[]} above
 */
const units = (above) => {
  ["art_1", "art_1__para_2", "art_2", "art_2__para_1"].forEach((id, n) => {
    const unit = document.createElement("section");
    unit.id = id;
    const top = above.includes(id) ? AT - 10 : AT + 100 * (n + 1);
    unit.getBoundingClientRect = () => DOMRect.fromRect({ y: top });
    document.body.append(unit);
  });
};

describe("follow in Read", () => {
  /** The outline of Read: Article 1 with its paragraph 2, then Article 2, folded. */
  const READ = `
    <act-outline>
      <nav class="outline">
        <p class="outline-follow" hidden><span class="where"></span></p>
        <ul>
          <li><details open><summary><a href="#art_1">Article 1 Subject</a></summary>
            <ul><li><a href="#art_1__para_2">2.</a></li></ul></details></li>
          <li><details><summary><a href="#art_2">Article 2 Definitions</a></summary>
            <ul><li><a href="#art_2__para_1">1.</a></li></ul></details></li>
        </ul>
      </nav>
    </act-outline>`;

  test("names nothing before the reader reaches the first unit", () => {
    document.body.innerHTML = READ;
    units([]);

    follow(document);

    expect(one(".outline-follow").hidden).toBe(true);
    expect(document.querySelector("[aria-current]")).toBeNull();
  });

  test("names the section in view and opens its branch", () => {
    document.body.innerHTML = READ;
    units(["art_1", "art_1__para_2", "art_2"]);

    follow(document);

    expect(one(".outline-follow").hidden).toBe(false);
    expect(one(".where").textContent).toBe("Article 2 Definitions");
    expect(one("[aria-current='location']").getAttribute("href")).toBe("#art_2");
    const branches = /** @type {NodeListOf<HTMLDetailsElement>} */ (
      document.querySelectorAll("details")
    );
    expect(branches[1]?.open).toBe(true);
  });

  test("marks the deepest entry the reader has reached", () => {
    document.body.innerHTML = READ;
    units(["art_1", "art_1__para_2"]);

    follow(document);

    expect(one("[aria-current='location']").getAttribute("href")).toBe("#art_1__para_2");
  });
});

describe("the rail button", () => {
  test("collapses the outline and remembers it", () => {
    document.body.innerHTML = OUTLINE;

    one(".outline-rail").click();

    expect(one(".outline").classList.contains("rail")).toBe(true);
    expect(localStorage.getItem(RAIL)).toBe("true");
  });
});

describe("ActOutline", () => {
  test("restores rail state from storage when connected", async () => {
    localStorage.setItem(RAIL, "true");
    document.body.innerHTML = OUTLINE;
    await Promise.resolve();

    expect(one(".outline").classList.contains("rail")).toBe(true);
  });

  test("toggles rail state when rail button is clicked", () => {
    document.body.innerHTML = OUTLINE;

    one(".outline-rail").click();
    expect(one(".outline").classList.contains("rail")).toBe(true);
    expect(localStorage.getItem(RAIL)).toBe("true");

    one(".outline-rail").click();
    expect(one(".outline").classList.contains("rail")).toBe(false);
    expect(localStorage.getItem(RAIL)).toBe("false");
  });
});
