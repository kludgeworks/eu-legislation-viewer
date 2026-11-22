// SPDX-License-Identifier: MIT
// @ts-check
/**
 * @file The tags of the text of a version on the Read page, legal/tag.jte.
 *
 * The hover text of each `<text-tag>` says what happened, which act did it and when that act
 * applies: "Replaced by 2026/449 · applies from 1 October 2026 · Article 2 of 2026/449". A click
 * on a tag, or Enter, opens the history of the provision of the tag, under the provision: the
 * acts that changed it. A second click closes it.
 *
 * The text is rendered without the act, so the page lists what it knows of each amending act in
 * `.tags`: a `span` with `data-act` and `data-applies`. `<provision-links>` has the address of
 * the histories (`data-href`), and lists the provisions that have a history (`data-occurrence`). A
 * listed provision that has no tag of its own gets a hollow sign, ▽, in the place of the tag. It
 * opens the history as a tag does.
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
 * Returns what the page lists of the act of a tag, or null if it lists nothing.
 *
 * @param {HTMLElement} tag
 * @returns {HTMLElement | null}
 */
const listed = (tag) => {
  const act = tag.dataset.act;
  if (!act) {
    return null;
  }
  for (const each of document.querySelectorAll(".tags [data-act]")) {
    if (each instanceof HTMLElement && each.dataset.act === act) {
      return each;
    }
  }
  return null;
};

/**
 * Returns the hover text of a tag. For example, "Replaced by 2026/449 · applies from 1 October
 * 2026 · Article 2 of 2026/449". A tag with no `data-modification` says "Amended". Without the date, it
 * says only what happened and which act did it.
 *
 * @param {HTMLElement} tag
 * @returns {string}
 */
export const said = (tag) => {
  const modification = tag.dataset.modification || "amended";
  const applies = listed(tag)?.dataset.applies;
  return `${modification.charAt(0).toUpperCase()}${modification.slice(1)} by ${number(tag.dataset.act)}${applies ? ` · ${applies}` : ""}`;
};

/**
 * Returns the occurrence of the text that `tag` is on: the nearest element with an id that holds it.
 *
 * @param {Element} tag
 * @returns {HTMLElement | null}
 */
export const occurrenceOf = (tag) => {
  const occurrence = tag.parentElement?.closest("[id]");
  return occurrence instanceof HTMLElement ? occurrence : null;
};

/**
 * Returns the address of the history of the provision that is `occurrence`, or null if the page does not
 * give the address of the histories.
 *
 * @param {HTMLElement} occurrence
 * @returns {string | null}
 */
export const history = (occurrence) => {
  const links = occurrence.ownerDocument.querySelector("provision-links");
  if (!(links instanceof HTMLElement) || !links.dataset.href) {
    return null;
  }
  return `${links.dataset.href}?occurrence=${encodeURIComponent(occurrence.id)}`;
};

/**
 * Returns the history of the provision that is `occurrence`, if it is open. The history of an occurrence with a
 * heading is after the heading. The history of any other occurrence is after the occurrence, below its words.
 * Each history has the eId of its occurrence in `data-occurrence`, because the history of an occurrence without a
 * heading is in the occurrence that holds it.
 *
 * @param {HTMLElement} occurrence
 * @returns {Element | null}
 */
const opened = (occurrence) =>
  [occurrence.querySelector(":scope > .title + .provision-history"), occurrence.nextElementSibling].find(
    (each) => each instanceof HTMLElement && each.dataset.occurrence === occurrence.id,
  ) ?? null;

/**
 * Puts `box`, the history of the provision that is `occurrence`, on the page: after the heading of the
 * occurrence, or after an occurrence that has no heading. Then the words of the occurrence stay together, and the
 * history is below them.
 *
 * @param {HTMLElement} occurrence
 * @param {Element} box
 */
const place = (occurrence, box) => {
  const heading = occurrence.querySelector(":scope > .title");
  if (heading) {
    heading.after(box);
  } else {
    occurrence.after(box);
  }
};

/**
 * Opens the history of the provision that is `occurrence` below it, or closes it if it is open. A
 * history that is below the window comes into view. `from`
 * is the tag or the link that the reader used. It says if the history is open (`aria-expanded`).
 *
 * @param {HTMLElement} occurrence
 * @param {HTMLElement} from
 * @returns {Promise<void>}
 */
export const toggle = async (occurrence, from) => {
  const open = opened(occurrence);
  if (open) {
    open.remove();
    from.setAttribute("aria-expanded", "false");
    return;
  }
  const href = history(occurrence);
  if (!href) {
    return;
  }
  const box = occurrence.ownerDocument.createElement("div");
  box.className = "provision-history";
  box.dataset.occurrence = occurrence.id;
  box.setAttribute("role", "status");
  box.textContent = "Loading the history…";
  place(occurrence, box);
  from.setAttribute("aria-expanded", "true");
  try {
    const response = await fetch(href, { headers: { Accept: "text/html" } });
    if (!response.ok) {
      throw new Error(`${response.status}`);
    }
    const made = new DOMParser().parseFromString(await response.text(), "text/html");
    const loaded = made.querySelector(".provision-history");
    if (!(loaded instanceof HTMLElement)) {
      throw new Error("no history");
    }
    loaded.dataset.occurrence = occurrence.id;
    box.replaceWith(loaded);
    loaded.scrollIntoView?.({ block: "nearest", behavior: "smooth" });
  } catch {
    box.textContent = "The history of this provision did not load. Try again.";
  }
};

/**
 * Custom element for a tag in the margin of a legal text.
 *
 * When connected, it sets its hover text if it has an amending act. Clicking the tag or pressing
 * Enter opens the history of its provision.
 */
export class TextTag extends HTMLElement {
  connectedCallback() {
    if (this.dataset.act && this.closest(".content")) {
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
    if (event.key === "Enter" || event.key === " ") {
      event.preventDefault();
      this.open();
    }
  };

  /**
   * Opens the history of the provision of this tag, or closes it.
   */
  open() {
    const occurrence = occurrenceOf(this);
    if (occurrence) {
      void toggle(occurrence, this);
    }
  }
}

/**
 * Gives each provision that `links` lists the sign ▽, if it is in `content` and has no tag of
 * its own. The sign is a button that opens the history of the provision. It does nothing if the text is not there yet, or has its links already.
 *
 * @param {HTMLElement} links
 * @param {HTMLElement} content
 */
export const link = (links, content) => {
  if (!content.querySelector("[id]") || content.querySelector(".provision-link")) {
    return;
  }
  for (const each of links.querySelectorAll("[data-occurrence]")) {
    if (!(each instanceof HTMLElement) || !each.dataset.occurrence) {
      continue;
    }
    const occurrence = content.ownerDocument.getElementById(each.dataset.occurrence);
    if (!occurrence || !content.contains(occurrence)) {
      continue;
    }
    const tagged = [...occurrence.querySelectorAll("text-tag")].some(
      (tag) => occurrenceOf(tag) === occurrence,
    );
    if (tagged) {
      continue;
    }
    const made = content.ownerDocument.createElement("button");
    made.type = "button";
    made.className = "provision-link";
    made.textContent = "▽";
    made.title = "History of this provision";
    made.setAttribute("aria-label", "History of this provision");
    made.setAttribute("aria-expanded", "false");
    made.addEventListener("click", () => {
      void toggle(occurrence, made);
    });
    occurrence.prepend(made);
  }
};

/**
 * Custom element that has the address of the histories of the provisions, and lists the
 * provisions that have a history. It puts the links on the text in the `.content` beside it after
 * each htmx swap, or at once if the text is there.
 */
export class ProvisionLinks extends HTMLElement {
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

  /** Puts the links on the text, if it is there. */
  apply() {
    const content = this.parentElement?.querySelector(":scope > .content");
    if (content instanceof HTMLElement) {
      link(this, content);
    }
  }
}

if (!customElements.get("text-tag")) {
  customElements.define("text-tag", TextTag);
}

if (!customElements.get("provision-links")) {
  customElements.define("provision-links", ProvisionLinks);
}
