// SPDX-License-Identifier: MIT
// @ts-check
/**
 * @file The theme switch of the site bar, layout/theme.jte.
 *
 * When the reader selects a theme, `<theme-switch>` sets it on the page at once. Then it posts the
 * choice with fetch to `POST /theme`, so the server keeps it and the page does not load again.
 * Without JavaScript, the form posts and the server sends the reader back to the page.
 */

/**
 * Applies the theme of a submitted theme form and posts it to the server.
 *
 * @param {SubmitEvent} event the submit event of a form on the page
 * @param {typeof fetch} send the function that posts the choice
 */
export const choose = (event, send) => {
  const form = event.target;
  const button = event.submitter;
  if (!(form instanceof HTMLFormElement) || !form.matches("form.theme") || !button) {
    return;
  }
  if (!(button instanceof HTMLButtonElement)) {
    return;
  }
  event.preventDefault();
  document.documentElement.dataset.theme = button.value;
  for (const each of form.querySelectorAll("button")) {
    each.setAttribute("aria-pressed", String(each === button));
  }
  send(form.action, {
    method: "POST",
    body: new URLSearchParams(
      /** @type {Record<string, string>} */ (
        Object.fromEntries(new FormData(form, button).entries())
      ),
    ),
  });
};

/**
 * Custom element that wraps the theme form and handles asynchronous submission.
 */
export class ThemeSwitch extends HTMLElement {
  connectedCallback() {
    this.addEventListener("submit", this.#handleSubmit);
  }

  disconnectedCallback() {
    this.removeEventListener("submit", this.#handleSubmit);
  }

  /**
   * @param {Event} event
   */
  #handleSubmit = (event) => {
    if (event instanceof SubmitEvent) {
      choose(event, fetch);
    }
  };
}

if (!customElements.get("theme-switch")) {
  customElements.define("theme-switch", ThemeSwitch);
}
