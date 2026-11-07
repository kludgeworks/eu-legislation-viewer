// SPDX-License-Identifier: MIT
// @ts-check
import { afterEach, describe, expect, test } from "vitest";
// The script adds its listener when it loads. The tests check that listener, so the import has no
// name.
// oxlint-disable-next-line import/no-unassigned-import
import "../../main/resources/static/js/receipt.js";

afterEach(() => {
  document.body.innerHTML = "";
});

/** @param {string} selector */
const click = (selector) =>
  /** @type {HTMLElement} */ (document.querySelector(selector)).click();

describe("the dismiss button of a receipt", () => {
  test("removes the receipt", () => {
    document.body.innerHTML = `
      <main><p class="receipt" role="status">✓ Sent · <button type="button" class="dismiss">dismiss</button></p></main>`;

    click("button.dismiss");

    expect(document.querySelector(".receipt")).toBeNull();
    expect(document.querySelector("main")).not.toBeNull();
  });

  test("leaves the receipt when the reader clicks elsewhere in it", () => {
    document.body.innerHTML = `
      <p class="receipt" role="status"><span>✓ Sent</span> · <button type="button" class="dismiss">dismiss</button></p>`;

    click(".receipt span");

    expect(document.querySelector(".receipt")).not.toBeNull();
  });
});
