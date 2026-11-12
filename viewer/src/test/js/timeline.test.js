// SPDX-License-Identifier: MIT
// @ts-check
import { afterEach, describe, expect, test } from "vitest";
import { next } from "../../main/resources/static/js/timeline.js";

afterEach(() => {
  document.body.innerHTML = "";
});

/**
 * Returns the dot with the given id.
 *
 * @param {string} id
 * @returns {HTMLElement}
 */
const dot = (id) => /** @type {HTMLElement} */ (document.getElementById(id));

describe("the ruler's keys", () => {
  test("move from dot to dot, and stop at the ends", () => {
    document.body.innerHTML = `<div class="ruler"><span class="dot adopted"></span>
      <a id="a" class="dot" href="/a"></a><a id="b" class="dot" href="/b"></a><a id="c" class="dot" href="/c"></a></div>`;

    expect(next(dot("b"), "ArrowLeft")).toBe(dot("a"));
    expect(next(dot("b"), "ArrowRight")).toBe(dot("c"));
    expect(next(dot("a"), "ArrowLeft")).toBe(dot("a"));
    expect(next(dot("a"), "End")).toBe(dot("c"));
    expect(next(dot("c"), "Home")).toBe(dot("a"));
    expect(next(dot("b"), "Enter")).toBeNull();
  });

  test("move the focus when a key is pressed on a dot", () => {
    document.body.innerHTML = `<div class="ruler"><a id="a" class="dot" href="/a"></a><a id="b" class="dot" href="/b"></a></div>`;
    dot("a").focus();

    dot("a").dispatchEvent(new KeyboardEvent("keydown", { key: "ArrowRight", bubbles: true }));

    expect(document.activeElement).toBe(dot("b"));
  });
});
