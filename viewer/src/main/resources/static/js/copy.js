// SPDX-License-Identifier: MIT
// @ts-check
/**
 * @file The copy button beside a number the reader may cite, such as a consolidated CELEX.
 *
 * A `<copy-text>` element holds the text to copy in `data-text` and a `button`. A click on the
 * button writes the text to the clipboard and says "copied" for a moment. Without the clipboard,
 * the button does nothing and the number stays selectable.
 */

/** How long the button says "copied", in milliseconds. */
export const SHOWN = 1500;

/**
 * Custom element that copies its `data-text` when its button is clicked.
 */
export class CopyText extends HTMLElement {
  connectedCallback() {
    this.addEventListener("click", this.#handleClick);
  }

  disconnectedCallback() {
    this.removeEventListener("click", this.#handleClick);
  }

  /**
   * @param {MouseEvent} event
   */
  #handleClick = async (event) => {
    const button = event.target instanceof Element ? event.target.closest("button") : null;
    const text = this.dataset.text;
    if (!button || !text || !navigator.clipboard) {
      return;
    }
    await navigator.clipboard.writeText(text);
    const label = button.textContent;
    button.textContent = "copied";
    setTimeout(() => {
      button.textContent = label;
    }, SHOWN);
  };
}

if (!customElements.get("copy-text")) {
  customElements.define("copy-text", CopyText);
}
