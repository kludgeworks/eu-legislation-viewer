// SPDX-License-Identifier: MIT
// @ts-check
/**
 * @file The outline beside the text of an act, in Read and in Compare (catalogue/text/ready.jte,
 * comparison/changes.jte and the other views).
 *
 * The `<act-outline>` element follows the reader. One IntersectionObserver watches the places the
 * outline leads to: the units its links go to in Read, and every difference in Compare. When the
 * reader passes one, the outline marks the entry for the part in view, opens the branches above
 * it, and scrolls itself to keep it in sight. Its head says where the reader is: in Compare
 * "Difference 663 of 1,828 · Annex IV", with ↑ and ↓ links to the difference before and after; in
 * Read the section in view.
 *
 * Without the script the outline is plain links, and its head shows the number of differences
 * (Compare) or nothing (Read).
 *
 * In Compare its button collapses the outline to a rail of the parts and their counts. The
 * browser of the reader remembers the choice in `localStorage`.
 */

/** The localStorage key that remembers if the outline is a rail. */
export const RAIL = "outline-rail";

/** How far from the top of the window, in pixels, a place must be to count as passed. */
export const AT = 140;

/**
 * Formats a number in English, for example 1,828.
 *
 * @param {number} n
 * @returns {string}
 */
export const format = (n) => n.toLocaleString("en");

/**
 * Returns true if the reader collapsed the outline to a rail before. It returns false if the
 * browser does not let the page read its storage.
 *
 * @param {Pick<Storage, "getItem">} storage
 * @returns {boolean}
 */
export const remembered = (storage) => {
  try {
    return storage.getItem(RAIL) === "true";
  } catch {
    return false;
  }
};

/**
 * Collapses the outline to a rail, or expands it, and labels its button to match.
 *
 * @param {Element} nav the outline
 * @param {boolean} on true to collapse it to a rail
 */
export const rail = (nav, on) => {
  nav.classList.toggle("rail", on);
  const button = nav.querySelector(".outline-rail");
  if (button instanceof HTMLElement) {
    button.setAttribute("aria-pressed", String(on));
    const label = on ? "Expand the outline" : "Collapse the outline";
    button.setAttribute("aria-label", label);
    button.title = label;
    button.textContent = on ? "⇥" : "⇤";
  }
};

/**
 * Returns the name of the part that contains a difference. The part is the last top entry of the
 * outline whose first difference is at or before the difference. It returns an empty string if
 * there is no such entry.
 *
 * @param {Element} nav the outline
 * @param {number} number the number of the difference
 * @returns {string}
 */
export const part = (nav, number) => {
  let found = "";
  for (const entry of nav.querySelectorAll(":scope > ul > li")) {
    const link = entry.querySelector("a.has");
    const first = link && Number((link.getAttribute("href") ?? "").replace("#difference-", ""));
    if (link && first && first <= number) {
      found = (link.querySelector("span")?.textContent ?? "").trim();
    }
  }
  return found;
};

/**
 * Returns the links of the outline's tree that go to a place in the page, in the outline's order.
 *
 * @param {Element} nav the outline
 * @returns {HTMLAnchorElement[]}
 */
const links = (nav) =>
  [...nav.querySelectorAll("ul a[href^='#']")].filter(
    /**
     * @param {Element} link
     * @returns {link is HTMLAnchorElement}
     */
    (link) => link instanceof HTMLAnchorElement && link.hash.length > 1,
  );

/**
 * Returns the places the outline follows, in the order of the page: every difference in Compare
 * (an outline with counts), the units the outline links to in Read.
 *
 * @param {Element} nav the outline
 * @param {Document} root
 * @returns {Element[]}
 */
export const places = (nav, root) => {
  if (nav.classList.contains("counted")) {
    return [...root.querySelectorAll("[id^='difference-']")];
  }
  const found = new Set();
  for (const link of links(nav)) {
    const place = root.getElementById(decodeURIComponent(link.hash.slice(1)));
    if (place) {
      found.add(place);
    }
  }
  return [...found].toSorted((a, b) =>
    a.compareDocumentPosition(b) & Node.DOCUMENT_POSITION_FOLLOWING ? -1 : 1,
  );
};

/**
 * Returns the index of the last place whose top the reader scrolled past, or -1 if there is none.
 * The places are in the order of the page, so their tops only go down.
 *
 * At the end of the page, the places below the line can never reach it. There the reader has
 * passed every place whose top is in the window.
 *
 * @param {Element[]} ordered
 * @param {boolean} [end] true if the reader is at the end of the page
 * @returns {number}
 */
export const passed = (ordered, end = atEnd()) => {
  const line = end ? window.innerHeight : AT;
  let low = 0;
  let high = ordered.length - 1;
  let found = -1;
  while (low <= high) {
    const middle = (low + high) >> 1;
    const place = ordered[middle];
    if (place && place.getBoundingClientRect().top <= line) {
      found = middle;
      low = middle + 1;
    } else {
      high = middle - 1;
    }
  }
  return found;
};

/**
 * Returns true if the reader scrolled to the end of the page, and the page is longer than the
 * window.
 *
 * @returns {boolean}
 */
const atEnd = () => {
  const page = document.documentElement;
  return window.scrollY > 0 && window.innerHeight + window.scrollY >= page.scrollHeight - 2;
};

/**
 * Returns the entry of the outline for the part the reader is in: the link to the latest place at
 * or before the place passed, the deepest one if several go there. It returns null if the reader
 * has passed none.
 *
 * @param {Element} nav the outline
 * @param {Document} root
 * @param {Element[]} ordered the places, in the order of the page
 * @param {number} index the index of the place passed, or -1
 * @returns {HTMLAnchorElement | null}
 */
export const entry = (nav, root, ordered, index) => {
  if (index < 0) {
    return null;
  }
  const at = new Map(ordered.map((place, i) => [place, i]));
  let found = null;
  let best = -1;
  for (const link of links(nav)) {
    const place = root.getElementById(decodeURIComponent(link.hash.slice(1)));
    const i = place ? at.get(place) : undefined;
    if (i !== undefined && i <= index && i >= best) {
      found = link;
      best = i;
    }
  }
  return found;
};

/**
 * Marks `link` as the part in view: it opens the branches above it, and scrolls the outline to
 * keep it in sight below the outline's head, which stays at its top. It clears the mark of the
 * entry before.
 *
 * @param {HTMLElement} nav the outline
 * @param {HTMLAnchorElement | null} link
 */
export const mark = (nav, link) => {
  const before = nav.querySelector("a[aria-current='location']");
  if (before === link) {
    return;
  }
  before?.removeAttribute("aria-current");
  if (!link) {
    return;
  }
  link.setAttribute("aria-current", "location");
  for (let up = link.parentElement; up && up !== nav; up = up.parentElement) {
    if (up instanceof HTMLDetailsElement) {
      up.open = true;
    }
  }
  const head = nav.querySelector(".outline-top");
  const covered = head instanceof HTMLElement ? head.offsetHeight : 0;
  const top = link.getBoundingClientRect().top - nav.getBoundingClientRect().top + nav.scrollTop;
  if (top < nav.scrollTop + covered || top + link.offsetHeight > nav.scrollTop + nav.clientHeight) {
    nav.scrollTop = Math.max(top - covered - (nav.clientHeight - covered) / 3, 0);
  }
};

/**
 * Updates the head of a comparison's outline to the difference the reader is at, `number`, and its
 * ↑ and ↓ links to the differences before and after. Before the first, it counts them.
 *
 * @param {Element} nav the outline
 * @param {number} number the number of the difference, or 0
 */
const counting = (nav, number) => {
  const line = nav.querySelector(".outline-follow");
  const where = line?.querySelector(".where");
  const up = line?.querySelector(".up");
  const down = line?.querySelector(".down");
  if (
    !(line instanceof HTMLElement) ||
    !where ||
    !(up instanceof HTMLAnchorElement) ||
    !(down instanceof HTMLAnchorElement)
  ) {
    return;
  }
  const total = Number(line.dataset.total);
  if (number === 0) {
    where.textContent = total === 1 ? "1 difference" : `${format(total)} differences`;
    up.setAttribute("aria-disabled", "true");
    down.href = "#difference-1";
    down.removeAttribute("aria-disabled");
    return;
  }
  const inPart = part(nav, number);
  where.textContent =
    `Difference ${format(number)} of ${format(total)}` + (inPart ? ` · ${inPart}` : "");
  up.href = `#difference-${Math.max(number - 1, 1)}`;
  up.toggleAttribute("aria-disabled", number <= 1);
  down.href = `#difference-${Math.min(number + 1, total)}`;
  down.toggleAttribute("aria-disabled", number >= total);
};

/**
 * Updates the outline in `root` to where the reader is: it marks the part in view, and says in its
 * head where the reader is. It does nothing if `root` has no outline.
 *
 * @param {Document} root
 * @param {Element[]} [ordered] the places the outline follows, if they are known
 */
export const follow = (root, ordered) => {
  const nav = root.querySelector("nav.outline");
  if (!(nav instanceof HTMLElement)) {
    return;
  }
  const all = ordered ?? places(nav, root);
  const index = passed(all);
  const link = entry(nav, root, all, index);
  mark(nav, link);
  if (nav.classList.contains("counted")) {
    counting(nav, Number(all[index]?.id.replace("difference-", "") ?? 0));
    return;
  }
  const line = nav.querySelector(".outline-follow");
  const where = line?.querySelector(".where");
  if (line instanceof HTMLElement && where) {
    where.textContent = link?.textContent?.trim() ?? "";
    line.hidden = !link;
  }
};

/**
 * Custom element for the outline beside an act's text. It follows the reader, and in Compare it
 * collapses to a rail.
 */
export class ActOutline extends HTMLElement {
  /** @type {IntersectionObserver | null} */
  #observer = null;
  /** @type {Element[]} */
  #places = [];
  /** @type {MutationObserver | null} */
  #waiting = null;

  connectedCallback() {
    // The outline can connect before its nav is parsed. Then it waits for the nav.
    if (!this.#start()) {
      this.#waiting = new MutationObserver(() => {
        if (this.#start()) {
          this.#waiting?.disconnect();
          this.#waiting = null;
        }
      });
      this.#waiting.observe(this, { childList: true });
    }
    // The text of Read loads after the outline, and a fold adds units when it opens.
    document.addEventListener("htmx:after:swap", this.#watch);
    window.addEventListener("scrollend", this.#update);
    this.addEventListener("click", this.#onClick);
  }

  /**
   * Starts to follow the reader, once the nav is there. Returns false if it is not yet.
   *
   * @returns {boolean}
   */
  #start() {
    const nav = this.querySelector("nav.outline");
    if (!nav) {
      return false;
    }
    if (nav.classList.contains("counted")) {
      rail(nav, remembered(localStorage));
    }
    this.#watch();
    return true;
  }

  disconnectedCallback() {
    this.#waiting?.disconnect();
    this.#waiting = null;
    this.#observer?.disconnect();
    this.#observer = null;
    document.removeEventListener("htmx:after:swap", this.#watch);
    window.removeEventListener("scrollend", this.#update);
    this.removeEventListener("click", this.#onClick);
  }

  /** Watches the places the outline follows, as the page has them now. */
  #watch = () => {
    const nav = this.querySelector("nav.outline");
    if (!nav || !this.isConnected) {
      return;
    }
    this.#observer?.disconnect();
    this.#places = places(nav, document);
    if (typeof IntersectionObserver === "function") {
      // A place crosses the line the reader is at, AT pixels from the top of the window.
      this.#observer = new IntersectionObserver(this.#update, {
        rootMargin: `-${AT}px 0px -50% 0px`,
      });
      for (const place of this.#places) {
        this.#observer.observe(place);
      }
    }
    this.#update();
  };

  #update = () => {
    if (this.isConnected) {
      follow(document, this.#places);
    }
  };

  /**
   * @param {MouseEvent} event
   */
  #onClick = (event) => {
    const button = event.target instanceof Element ? event.target.closest(".outline-rail") : null;
    const nav = this.querySelector("nav.outline.counted");
    if (!button || !nav) {
      return;
    }
    const on = !nav.classList.contains("rail");
    rail(nav, on);
    try {
      localStorage.setItem(RAIL, String(on));
    } catch {
      // The browser does not let the page keep it. The choice holds for this page only.
    }
  };
}

if (!customElements.get("act-outline")) {
  customElements.define("act-outline", ActOutline);
}
