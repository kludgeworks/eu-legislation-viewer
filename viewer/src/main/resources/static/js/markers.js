// SPDX-License-Identifier: MIT
// @ts-check
/**
 * @file The amendment marks of the text of a version on the Read page, legal/provenance.jte.
 *
 * The hover text of each `<amendment-mark>` says what happened, which act did it and when that act
 * applies: "Replaced by 2026/449 · applies from 1 October 2026 · Article 2 of 2026/449". A click
 * on a mark, or Enter, opens the step that made the change in Compare, at the unit of the mark.
 *
 * The text is rendered without the act, so the page lists what it knows of each amending act in
 * `.marks`: a `span` with `data-act`, `data-applies` and `data-compare`. A mark whose act is not
 * listed opens the act's change from the text's `data-compare`.
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
 * Returns what the page lists of the act of a mark, or null if it lists nothing.
 *
 * @param {HTMLElement} mark
 * @returns {HTMLElement | null}
 */
const listed = (mark) => {
  const act = mark.dataset.act;
  if (!act) {
    return null;
  }
  for (const each of document.querySelectorAll(".marks [data-act]")) {
    if (each instanceof HTMLElement && each.dataset.act === act) {
      return each;
    }
  }
  return null;
};

/**
 * Returns the hover text of a mark. For example, "Replaced by 2026/449 · applies from 1 October
 * 2026 · Article 2 of 2026/449". A mark with no `data-action` says "Amended". Without the date, it
 * says only what happened and which act did it.
 *
 * @param {HTMLElement} mark
 * @returns {string}
 */
export const said = (mark) => {
  const action = mark.dataset.action || "amended";
  const applies = listed(mark)?.dataset.applies;
  return `${action.charAt(0).toUpperCase()}${action.slice(1)} by ${number(mark.dataset.act)}${applies ? ` · ${applies}` : ""}`;
};

/**
 * Returns the address of the comparison that shows the change of the mark's act: the step that
 * made it, at the unit of the mark. A mark whose act the page does not list opens the act's change
 * from the text's comparisons. It returns null if the mark has no act or is not in a text that has
 * a comparison.
 *
 * @param {HTMLElement} mark
 * @returns {string | null}
 */
export const change = (mark) => {
  const text = mark.closest("[data-compare]");
  if (!(text instanceof HTMLElement) || !mark.dataset.act) {
    return null;
  }
  const step = listed(mark)?.dataset.compare;
  if (!step) {
    return `${text.dataset.compare}?act=${encodeURIComponent(mark.dataset.act)}`;
  }
  const unit = mark.parentElement?.closest("[id]");
  return unit && text.contains(unit) ? `${step}#${encodeURIComponent(unit.id)}` : step;
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
