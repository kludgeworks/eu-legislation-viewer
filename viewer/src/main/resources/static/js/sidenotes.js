// SPDX-License-Identifier: MIT
// @ts-check
/**
 * @file The parts that the sidenotes of a comparison repeat, comparison/sidenoteparts.jte.
 *
 * The view sends each part once, in `<template id="sidenote-parts">`, before the differences. A
 * sidenote names its parts by id. `<made-by>` takes its hover text from its part: how the acts
 * that made the difference were published. `<shared-dates>` fills its fold of dates from its part
 * when the reader first opens it.
 */

/**
 * Returns the part with the id `id`, or null if the page has no such part.
 *
 * @param {string | undefined} id
 * @returns {Element | null}
 */
export const part = (id) => {
  const parts = document.getElementById("sidenote-parts");
  if (!id || !(parts instanceof HTMLTemplateElement)) {
    return null;
  }
  return parts.content.getElementById(id);
};

/**
 * Custom element for who made a difference, for example "Replaced by 2024/2609 and 2025/115
 * together". Its hover text is its part, named by `data-published`.
 */
export class MadeBy extends HTMLElement {
  connectedCallback() {
    const published = part(this.dataset.published);
    if (published) {
      this.title = published.textContent ?? "";
    }
  }
}

/**
 * Custom element around a fold of dates, `<details class="dates">`. When the reader first opens
 * the fold, it copies the date lines of its part, named by `data-dates`, after the summary.
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
    const dates = part(this.dataset.dates);
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
