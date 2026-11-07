// SPDX-License-Identifier: MIT
// @ts-check
/**
 * @file The outline of a comparison, catalogue/outlinehead.jte. htmx loads it with its view.
 *
 * The head of the outline follows the reader as they scroll. It says which difference the reader
 * is at, for example "Difference 663 of 1,828 · Annex IV". Its ↑ and ↓ links go to the difference
 * before and after. Its button collapses the outline to a rail of the parts and their counts. The
 * browser of the reader remembers the choice.
 */

/** The localStorage key that remembers if the outline is a rail. */
export const RAIL = "outline-rail";

/** How far from the top of the window, in pixels, a difference must be to count as passed. */
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
 * Returns the number of the last difference whose top the reader scrolled past, or 0 if there is
 * none.
 *
 * @param {ParentNode} root
 * @returns {number}
 */
export const at = (root) => {
  let number = 0;
  for (const difference of root.querySelectorAll("[id^='difference-']")) {
    if (difference.getBoundingClientRect().top > AT) {
      break;
    }
    number = Number(difference.id.replace("difference-", ""));
  }
  return number;
};

/**
 * Updates the head of the outline in `root` to the difference the reader is at, and its ↑ and ↓
 * links to the differences before and after. It does nothing if `root` has no outline with
 * counts.
 *
 * @param {ParentNode} root
 */
export const follow = (root) => {
  const nav = root.querySelector("nav.outline.counted");
  const line = nav?.querySelector(".outline-follow");
  const where = line?.querySelector(".where");
  const up = line?.querySelector(".up");
  const down = line?.querySelector(".down");
  if (
    !nav ||
    !(line instanceof HTMLElement) ||
    !where ||
    !(up instanceof HTMLAnchorElement) ||
    !(down instanceof HTMLAnchorElement)
  ) {
    return;
  }
  const total = Number(line.dataset.total);
  const number = at(root);
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

let pending = false;

/** Updates the head of the outline once, at the next frame, however often the page scrolls. */
const soon = () => {
  if (!pending) {
    pending = true;
    requestAnimationFrame(() => {
      pending = false;
      follow(document);
    });
  }
};

document.addEventListener("scroll", soon, { passive: true });
document.addEventListener("click", (event) => {
  const button = event.target instanceof Element ? event.target.closest(".outline-rail") : null;
  const nav = button?.closest("nav.outline");
  if (!nav) {
    return;
  }
  const on = !nav.classList.contains("rail");
  rail(nav, on);
  try {
    localStorage.setItem(RAIL, String(on));
  } catch {
    // The browser does not let the page keep it. The choice holds for this page only.
  }
});
document.addEventListener("htmx:after:settle", () => {
  for (const nav of document.querySelectorAll("nav.outline.counted")) {
    rail(nav, remembered(localStorage));
  }
  follow(document);
});
