// SPDX-License-Identifier: MIT
// @ts-check
/**
 * @file The divergence notes on the Read page, beside the text where it is not the law on the day
 * asked, catalogue/text/ready.jte.
 *
 * The content module renders the text without the act, and htmx loads it into `.content`. So the
 * page lists the notes in `<divergence-notes>`, next to the text. Each note names an occurrence by
 * its eId (`data-occurrence`).
 *
 * - A `.prospective-note` is for a prospective amendment, a change that does not apply on the day.
 *   Its occurrence gets an amber bar (`.not-applying`) and the note beside it: the act, as a link to
 *   its page, and the state. With `data-heading`, only the heading of the occurrence gets the bar.
 *   An occurrence in an occurrence that has the bar already gets no bar of its own. An occurrence
 *   right after an occurrence with the bar and the same words has no note of its own: one note
 *   serves the run.
 * - A `.pending-note` is for a pending amendment, a change that the text does not show. It goes
 *   before its occurrence, or at the end of the occurrence with `data-end`.
 *
 * The outline beside the text gets a dot on each entry that has a note, and on the entries that
 * hold it: `.has-prospective` or `.has-pending`, with words for a screen reader.
 */

/**
 * Returns whether `occurrence` comes right after an occurrence that has the bar with `label`, or
 * whose heading has it. Then the note of that occurrence serves this one.
 *
 * @param {HTMLElement} occurrence
 * @param {string} label
 * @returns {boolean}
 */
const follows = (occurrence, label) => {
  const previous = occurrence.previousElementSibling;
  if (!(previous instanceof HTMLElement)) {
    return false;
  }
  const heading = previous.querySelector(":scope > .title");
  return (
    previous.dataset.notApplying === label ||
    (heading instanceof HTMLElement && heading.dataset.notApplying === label)
  );
};

/** The words that a screen reader gets for each dot of the outline. */
const DOTS = {
  "has-prospective": " (has a change that does not apply on the day)",
  "has-pending": " (has a change that this text does not show)",
};

/**
 * Gives the outline a dot for a note on `occurrence`: on the entry of the occurrence, or of the nearest occurrence
 * that holds it and has an entry, and on each entry that holds that entry.
 *
 * @param {Element | null} outline
 * @param {HTMLElement} occurrence
 * @param {"has-prospective" | "has-pending"} dot
 */
const dotOutline = (outline, occurrence, dot) => {
  if (!outline) {
    return;
  }
  /** @type {Element | null} */
  let held = occurrence;
  /** @type {Element | null} */
  let entry = null;
  while (held && !entry) {
    entry = held.id ? outline.querySelector(`a[href="#${CSS.escape(held.id)}"]`) : null;
    held = held.parentElement ? held.parentElement.closest("[id]") : null;
  }
  for (let item = entry?.closest("li"); item; item = item.parentElement?.closest("li")) {
    const link = item.querySelector(":scope > a, :scope > details > summary > a");
    if (link && !link.classList.contains(dot)) {
      link.classList.add(dot);
      const words = link.ownerDocument.createElement("span");
      words.className = "visually-hidden";
      words.textContent = DOTS[dot];
      link.append(words);
    }
  }
};

/**
 * Returns a copy of the note `each` for the text, without the data that places it.
 *
 * @param {HTMLElement} each
 * @returns {HTMLElement}
 */
const copy = (each) => {
  const made = /** @type {HTMLElement} */ (each.cloneNode(true));
  delete made.dataset.occurrence;
  delete made.dataset.heading;
  delete made.dataset.end;
  return made;
};

/**
 * Puts the notes that `notes` lists on the text in `content`, and the dots on `outline`. It does
 * nothing if the text is not there yet, or has its notes already.
 *
 * @param {HTMLElement} notes
 * @param {HTMLElement} content
 * @param {Element | null} [outline]
 */
export const place = (notes, content, outline = null) => {
  if (!content.querySelector("[id]") || content.querySelector(".not-applying, .pending-note")) {
    return;
  }
  for (const each of notes.querySelectorAll("[data-occurrence]")) {
    if (!(each instanceof HTMLElement) || !each.dataset.occurrence) {
      continue;
    }
    const occurrence = content.ownerDocument.getElementById(each.dataset.occurrence);
    if (!occurrence || !content.contains(occurrence)) {
      continue;
    }
    if (each.classList.contains("pending-note")) {
      if (each.dataset.end === "true") {
        occurrence.append(copy(each));
      } else {
        occurrence.before(copy(each));
      }
      dotOutline(outline, occurrence, "has-pending");
      continue;
    }
    dotOutline(outline, occurrence, "has-prospective");
    if (occurrence.closest(".not-applying")) {
      continue;
    }
    const heading = each.dataset.heading === "true" ? occurrence.querySelector(":scope > .title") : null;
    const target = heading instanceof HTMLElement ? heading : occurrence;
    target.classList.add("not-applying");
    const label = each.textContent?.trim() ?? "";
    target.dataset.notApplying = label;
    if (!follows(target, label)) {
      target.append(copy(each));
    }
  }
};

/**
 * Custom element that lists the divergence notes. It puts them on the text in the `.content` beside
 * it after each htmx swap, or at once if the text is there. A swap of anything else leaves the text
 * as it is, and a text that has its notes keeps them.
 */
export class DivergenceNotes extends HTMLElement {
  connectedCallback() {
    document.addEventListener("htmx:after:swap", this.#handleSwap);
    this.apply();
  }

  disconnectedCallback() {
    document.removeEventListener("htmx:after:swap", this.#handleSwap);
  }

  #handleSwap = () => {
    this.apply();
  };

  /** Puts the notes on the text, if it is there. */
  apply() {
    const content = this.#content();
    if (content) {
      place(this, content, this.parentElement?.querySelector(":scope nav.outline") ?? null);
    }
  }

  /**
   * Returns the element that holds the text, beside this one.
   *
   * @returns {HTMLElement | null}
   */
  #content() {
    const content = this.parentElement?.querySelector(":scope > .content");
    return content instanceof HTMLElement ? content : null;
  }
}

if (!customElements.get("divergence-notes")) {
  customElements.define("divergence-notes", DivergenceNotes);
}
