// SPDX-License-Identifier: MIT
// @ts-check
import { afterEach, describe, expect, test, vi } from "vitest";
// The custom element registers itself when the script loads.
// oxlint-disable-next-line import/no-unassigned-import
import "../../main/resources/static/js/theme.js";

afterEach(() => {
  vi.unstubAllGlobals();
  document.body.innerHTML = "";
  delete document.documentElement.dataset.theme;
});

/**
 * Submits the theme form with the button for a theme, as a click on it does.
 *
 * @param {string} theme
 */
const select = (theme) => {
  const form = /** @type {HTMLFormElement} */ (document.querySelector("form"));
  const button = /** @type {HTMLButtonElement} */ (
    form.querySelector(`button[value="${theme}"]`)
  );
  form.dispatchEvent(new SubmitEvent("submit", { submitter: button, bubbles: true, cancelable: true }));
};

describe("ThemeSwitch", () => {
  test("sets the theme at once and posts it, without loading the page again", () => {
    const send = vi.fn(() => Promise.resolve(new Response(null, { status: 204 })));
    vi.stubGlobal("fetch", send);
    document.body.innerHTML = `
      <theme-switch>
        <form class="theme" method="post" action="/theme">
          <button name="theme" value="light" aria-pressed="true">Light</button>
          <button name="theme" value="dark" aria-pressed="false">Dark</button>
        </form>
      </theme-switch>`;

    select("dark");

    expect(document.documentElement.dataset.theme).toBe("dark");
    expect(
      [...document.querySelectorAll("button")].map((b) => b.getAttribute("aria-pressed")),
    ).toEqual(["false", "true"]);
    expect(send).toHaveBeenCalledOnce();
    const [url, init] = /** @type {[string, RequestInit]} */ (
      /** @type {unknown} */ (send.mock.calls[0])
    );
    expect(new URL(url).pathname).toBe("/theme");
    expect(init.method).toBe("POST");
    expect(String(init.body)).toBe("theme=dark");
  });

  test("leaves other forms alone", () => {
    const send = vi.fn();
    vi.stubGlobal("fetch", send);
    document.body.innerHTML = `
      <theme-switch>
        <form class="theme" method="post" action="/theme">
          <button name="theme" value="light" aria-pressed="true">Light</button>
        </form>
      </theme-switch>
      <form class="find" method="get" action="/">
        <button name="theme" value="dark">Find</button>
      </form>`;

    const find = /** @type {HTMLFormElement} */ (document.querySelector("form.find"));
    const button = /** @type {HTMLButtonElement} */ (find.querySelector("button"));
    find.dispatchEvent(new SubmitEvent("submit", { submitter: button, bubbles: true, cancelable: true }));

    expect(document.documentElement.dataset.theme).toBeUndefined();
    expect(send).not.toHaveBeenCalled();
  });
});
