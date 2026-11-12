// SPDX-License-Identifier: MIT
// @ts-check
import { afterEach, describe, expect, test } from "vitest";
import { part } from "../../main/resources/static/js/sidenotes.js";

afterEach(() => {
  document.body.innerHTML = "";
});

const PARTS = `
  <template id="sidenote-parts"><span id="published-1">2024/2609: Commission Regulation (EU) 2024/2609
2024/2619: Commission Regulation (EU) 2024/2619</span><div id="dates-1"><span class="applies">2024/2609 applies from 28 April 2025</span><span class="applies">2024/2619 applies from 29 April 2025</span></div></template>`;

describe("part", () => {
  test("finds a part in the template, and none without one", () => {
    document.body.innerHTML = PARTS;

    expect(part("dates-1")?.children).toHaveLength(2);
    expect(part("dates-2")).toBeNull();
    expect(part(undefined)).toBeNull();
  });
});

describe("MadeBy", () => {
  test("takes its hover text from its part", () => {
    document.body.innerHTML = `${PARTS}<made-by class="made" data-published="published-1">Replaced by 2024/2609 and 2024/2619 together</made-by>`;

    const made = /** @type {HTMLElement} */ (document.querySelector("made-by"));

    expect(made.title).toBe(
      "2024/2609: Commission Regulation (EU) 2024/2609\n2024/2619: Commission Regulation (EU) 2024/2619",
    );
  });

  test("has no hover text when its part is not sent", () => {
    document.body.innerHTML = `<made-by class="made" data-published="published-1">Replaced by 2024/2609</made-by>`;

    expect(/** @type {HTMLElement} */ (document.querySelector("made-by")).title).toBe("");
  });
});

describe("SharedDates", () => {
  test("copies its date lines into the fold when it opens, once", () => {
    document.body.innerHTML = `${PARTS}<shared-dates data-dates="dates-1"><details class="dates"><summary>2 dates · <span class="show">show</span></summary></details></shared-dates>`;
    const shared = /** @type {import("../../main/resources/static/js/sidenotes.js").SharedDates} */ (
      document.querySelector("shared-dates")
    );
    const details = /** @type {HTMLDetailsElement} */ (shared.querySelector("details"));

    shared.fill();
    expect(details.querySelectorAll(".applies")).toHaveLength(0);

    details.open = true;
    shared.fill();
    shared.fill();

    expect(Array.from(details.querySelectorAll(".applies"), (line) => line.textContent)).toEqual([
      "2024/2609 applies from 28 April 2025",
      "2024/2619 applies from 29 April 2025",
    ]);
    // The part stays in the template for the other sidenotes
    expect(part("dates-1")?.children).toHaveLength(2);
  });
});
