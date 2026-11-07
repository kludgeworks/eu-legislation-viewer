// SPDX-License-Identifier: MIT
// @ts-check
import { afterEach, describe, expect, test } from "vitest";
import {
  AT,
  RAIL,
  follow,
  format,
  part,
  rail,
  remembered,
} from "../../main/resources/static/js/outline.js";

afterEach(() => {
  document.body.innerHTML = "";
  localStorage.clear();
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
  </nav>`;

/**
 * Adds differences 1 to 4 to the page, the first `passed` of them above the line the reader is at.
 *
 * @param {number} passed
 */
const differences = (passed) => {
  for (let n = 1; n <= 4; n++) {
    const difference = document.createElement("section");
    difference.id = `difference-${n}`;
    const top = n <= passed ? AT - 10 : AT + 100 * n;
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
    const nav = one("nav");

    rail(nav, true);
    expect(nav.classList.contains("rail")).toBe(true);
    expect(one(".outline-rail").getAttribute("aria-label")).toBe("Expand the outline");

    rail(nav, false);
    expect(nav.classList.contains("rail")).toBe(false);
    expect(one(".outline-rail").getAttribute("aria-pressed")).toBe("false");
  });
});

describe("part", () => {
  test("names the last part that starts at or before the difference", () => {
    document.body.innerHTML = OUTLINE;

    expect(part(one("nav"), 2)).toBe("Article 1");
    expect(part(one("nav"), 3)).toBe("Annex IV");
    expect(part(one("nav"), 0)).toBe("");
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

describe("the rail button", () => {
  test("collapses the outline and remembers it", () => {
    document.body.innerHTML = OUTLINE;

    one(".outline-rail").click();

    expect(one("nav").classList.contains("rail")).toBe(true);
    expect(localStorage.getItem(RAIL)).toBe("true");
  });
});
