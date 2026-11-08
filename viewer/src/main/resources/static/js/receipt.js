// SPDX-License-Identifier: MIT
// @ts-check
/**
 * @file The dismiss button of a receipt.
 *
 * An `<action-receipt>` element shows that the server completed what the reader asked. Its button
 * `button.dismiss` removes the receipt from the page.
 */

/**
 * Custom element that removes itself when its dismiss button is clicked.
 */
export class ActionReceipt extends HTMLElement {
  connectedCallback() {
    this.addEventListener("click", this.#handleClick);
  }

  disconnectedCallback() {
    this.removeEventListener("click", this.#handleClick);
  }

  /**
   * @param {MouseEvent} event
   */
  #handleClick = (event) => {
    if (event.target instanceof Element && event.target.closest("button.dismiss")) {
      this.remove();
    }
  };
}

if (!customElements.get("action-receipt")) {
  customElements.define("action-receipt", ActionReceipt);
}
