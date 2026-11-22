// SPDX-License-Identifier: MIT
// @ts-check
/**
 * @file The pieces that the sidenotes of a comparison repeat, comparison/sidenotepieces.jte.
 *
 * The view sends each piece once, in `<template id="sidenote-pieces">`, before the differences. A
 * sidenote names its pieces by id. `<made-by>` takes its hover text from its piece: how the acts
 * that made the difference were published. `<shared-dates>` fills its fold of dates from its piece
 * when the reader first opens it.
 */

/**
 * Returns the piece with the id `id`, or null if the page has no such piece.
 *
 * @param {string | undefined} id
 * @returns {Element | null}
 */
export const piece = (id) => {
  const pieces = document.getElementById("sidenote-pieces");
  if (!id || !(pieces instanceof HTMLTemplateElement)) {
    return null;
  }
  return pieces.content.getElementById(id);
};

/**
 * Custom element for who made a difference, for example "Replaced by 2024/2609 and 2025/115
 * together". Its hover text is its piece, named by `data-published`.
 */
export class MadeBy extends HTMLElement {
  connectedCallback() {
    const published = piece(this.dataset.published);
    if (published) {
      this.title = published.textContent ?? "";
    }
  }
}

/**
 * Custom element around a fold of dates, `<details class="dates">`. When the reader first opens
 * the fold, it copies the date lines of its piece, named by `data-dates`, after the summary.
 */
export class SharedDates extends HTMLElement {
  connectedCallback() {
    this.addEventListener("toggle", this.#handleToggle, true);
  }

  disconnectedCallback() {
    this.removeEventListener("toggle", this.#handleToggle, true);
  }

  #handleToggle = () => {
    this.fill();
  };

  /** Copies the date lines into the fold, once. */
  fill() {
    const details = this.querySelector("details");
    const dates = piece(this.dataset.dates);
    if (!details?.open || !dates || details.querySelector(".applies")) {
      return;
    }
    details.append(...Array.from(dates.childNodes, (node) => node.cloneNode(true)));
  }
}

if (!customElements.get("made-by")) {
  customElements.define("made-by", MadeBy);
}
if (!customElements.get("shared-dates")) {
  customElements.define("shared-dates", SharedDates);
}
