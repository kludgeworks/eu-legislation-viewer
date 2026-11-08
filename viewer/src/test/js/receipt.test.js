// SPDX-License-Identifier: MIT
// @ts-check
import { afterEach, describe, expect, test } from "vitest";
// The custom element registers itself when the script loads.
// oxlint-disable-next-line import/no-unassigned-import
import "../../main/resources/static/js/receipt.js";

afterEach(() => {
  document.body.innerHTML = "";
});

/** @param {string} selector */
const click = (selector) =>
  /** @type {HTMLElement} */ (document.querySelector(selector)).click();

describe("ActionReceipt", () => {
  test("removes the receipt when dismiss is clicked", () => {
    document.body.innerHTML = `
      <main><action-receipt class="receipt" role="status">✓ Sent · <button type="button" class="dismiss">dismiss</button></action-receipt></main>`;

    click("button.dismiss");

    expect(document.querySelector(".receipt")).toBeNull();
    expect(document.querySelector("main")).not.toBeNull();
  });

  test("leaves the receipt when the reader clicks elsewhere in it", () => {
    document.body.innerHTML = `
      <action-receipt class="receipt" role="status"><span>✓ Sent</span> · <button type="button" class="dismiss">dismiss</button></action-receipt>`;

    click(".receipt span");

    expect(document.querySelector(".receipt")).not.toBeNull();
  });
});
