// SPDX-License-Identifier: MIT
// @ts-check
/**
 * @file The marks in the margin of the text on the Read page, where the text is not the law on
 * the day asked, catalogue/text/ready.jte.
 *
 * The content module renders the text without the act, and htmx loads it into `.content`. So the
 * page lists the marks in `<margin-rules>`, next to the text. Each `span` names a unit by its eId
 * (`data-unit`), whether only its heading is marked (`data-heading`), and the words of its link
 * (`data-label`). A `.margin-note` is the note for a text that does not apply at all on the day.
 *
 * When the text arrives, each unit gets a dashed amber rule (`.divergent`) and a link up to the
 * warning in the Reading card, `#divergences`. A unit in a unit that is marked already is not
 * marked again. A unit right after a marked unit with the same words has no link of its own: one
 * link serves the run. The note goes to the top of the text, and the whole text gets the rule.
 */

/**
 * Returns whether `unit` comes right after a unit that is marked with `label`, or whose heading
 * is. Then the mark of that unit has the link.
 *
 * @param {HTMLElement} unit
 * @param {string} label
 * @returns {boolean}
 */
const follows = (unit, label) => {
  const previous = unit.previousElementSibling;
  if (!(previous instanceof HTMLElement)) {
    return false;
  }
  const heading = previous.querySelector(":scope > .title");
  return (
    previous.dataset.divergent === label ||
    (heading instanceof HTMLElement && heading.dataset.divergent === label)
  );
};

/**
 * Puts the marks that `rules` lists on the text in `content`. It does nothing if the text is not
 * there yet, or has its marks already.
 *
 * @param {HTMLElement} rules
 * @param {HTMLElement} content
 */
export const mark = (rules, content) => {
  if (!content.querySelector("[id]") || content.querySelector(".divergent, .margin-note")) {
    return;
  }
  const note = rules.querySelector(".margin-note");
  if (note) {
    content.prepend(note.cloneNode(true));
    content.classList.add("divergent-whole");
  }
  for (const each of rules.querySelectorAll("span[data-unit]")) {
    if (!(each instanceof HTMLElement) || !each.dataset.unit) {
      continue;
    }
    const unit = content.ownerDocument.getElementById(each.dataset.unit);
    if (!unit || !content.contains(unit) || unit.closest(".divergent")) {
      continue;
    }
    const heading = each.dataset.heading === "true" ? unit.querySelector(":scope > .title") : null;
    const target = heading instanceof HTMLElement ? heading : unit;
    target.classList.add("divergent");
    const label = each.dataset.label ?? "";
    target.dataset.divergent = label;
    if (follows(target, label)) {
      continue;
    }
    const link = content.ownerDocument.createElement("a");
    link.className = "divergent-link";
    link.href = "#divergences";
    link.textContent = `${label} ↑`;
    target.append(link);
  }
};

/**
 * Custom element that lists the marks of the margin. It puts them on the text in the `.content`
 * beside it after each htmx swap, or at once if the text is there. A swap of anything else leaves
 * the text as it is, and a text that has its marks keeps them.
 */
export class MarginRules extends HTMLElement {
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

  /** Puts the marks on the text, if it is there. */
  apply() {
    const content = this.#content();
    if (content) {
      mark(this, content);
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

if (!customElements.get("margin-rules")) {
  customElements.define("margin-rules", MarginRules);
}
