// SPDX-License-Identifier: MIT
// @ts-check
/**
 * @file The amendment marks of the text of a version on the Read page, legal/provenance.jte.
 *
 * The hover text of each `<amendment-mark>` says what happened and which act did it, for example
 * "Replaced by 2017/815". A click on a mark, or Enter, opens the change of that act in Compare.
 * The text's container holds the address of the comparison in `data-compare`.
 */

/**
 * Returns the number of an act from its CELEX number. For example, 32017R0815 gives "2017/815",
 * and 32019R0103R(01) gives "Corrigendum 1 to 2019/103". A value that is not a CELEX number of an
 * act is returned as it is.
 *
 * @param {string | undefined} celex
 * @returns {string}
 */
export const number = (celex) => {
  const act = /^\d(\d{4})[A-Z](\d+)(?:R\((\d+)\))?$/.exec(celex ?? "");
  if (!act) {
    return celex ?? "";
  }
  const name = `${act[1]}/${Number(act[2])}`;
  return act[3] ? `Corrigendum ${Number(act[3])} to ${name}` : name;
};

/**
 * Returns the hover text of a mark. For example, "Replaced by 2017/815 · open this change in
 * Compare". A mark with no `data-action` says "Amended".
 *
 * @param {HTMLElement} mark
 * @returns {string}
 */
export const said = (mark) => {
  const action = mark.dataset.action || "amended";
  return `${action.charAt(0).toUpperCase()}${action.slice(1)} by ${number(mark.dataset.act)} · open this change in Compare`;
};

/**
 * Returns the address of the comparison that shows the change of the mark's act. It returns null
 * if the mark has no act or is not in a text that has a comparison.
 *
 * @param {HTMLElement} mark
 * @returns {string | null}
 */
export const change = (mark) => {
  const text = mark.closest("[data-compare]");
  if (!(text instanceof HTMLElement) || !mark.dataset.act) {
    return null;
  }
  return `${text.dataset.compare}?act=${encodeURIComponent(mark.dataset.act)}`;
};

/**
 * Custom element for an amendment mark in the margin of a legal text.
 *
 * When connected, it sets its hover text if it has an amending act. Clicking the mark or pressing
 * Enter opens the comparison for that act.
 */
export class AmendmentMark extends HTMLElement {
  connectedCallback() {
    if (this.dataset.act && this.closest("[data-compare]")) {
      this.title = said(this);
    }
    this.addEventListener("click", this.#handleClick);
    this.addEventListener("keydown", this.#handleKeyDown);
  }

  disconnectedCallback() {
    this.removeEventListener("click", this.#handleClick);
    this.removeEventListener("keydown", this.#handleKeyDown);
  }

  #handleClick = () => {
    this.open();
  };

  /**
   * @param {KeyboardEvent} event
   */
  #handleKeyDown = (event) => {
    if (event.key === "Enter") {
      this.open();
    }
  };

  /**
   * Opens the change of this mark, if it has one.
   */
  open() {
    const href = change(this);
    if (href) {
      location.assign(href);
    }
  }
}

if (!customElements.get("amendment-mark")) {
  customElements.define("amendment-mark", AmendmentMark);
}
