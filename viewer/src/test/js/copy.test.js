// SPDX-License-Identifier: MIT
// @ts-check
import { afterEach, describe, expect, test, vi } from "vitest";
import { SHOWN } from "../../main/resources/static/js/copy.js";

afterEach(() => {
  document.body.innerHTML = "";
  vi.useRealTimers();
  vi.unstubAllGlobals();
  vi.restoreAllMocks();
});

describe("copy-text", () => {
  test("copies its text and says so for a moment", async () => {
    vi.useFakeTimers();
    const writeText = vi.fn(() => Promise.resolve());
    vi.stubGlobal("navigator", { clipboard: { writeText } });
    document.body.innerHTML = `<copy-text data-text="02015R1998-20211231"><button type="button">copy</button></copy-text>`;
    const button = /** @type {HTMLButtonElement} */ (document.querySelector("button"));

    button.click();
    await Promise.resolve();

    expect(writeText).toHaveBeenCalledWith("02015R1998-20211231");
    expect(button.textContent).toBe("copied");
    vi.advanceTimersByTime(SHOWN);
    expect(button.textContent).toBe("copy");
  });
});
