// SPDX-License-Identifier: MIT
// @ts-check
/**
 * @file The keys of the book ruler (catalogue/timeline.jte).
 *
 * Each version's dot is a link. On a dot, the left and right arrow keys move to the dot before and
 * after, and Home and End to the first and the last. Enter follows the link, as on any link.
 */

/**
 * Returns the dot that a key moves to from `dot`, or null if the key does not move.
 *
 * @param {Element} dot the dot that has the focus
 * @param {string} key the key, as `KeyboardEvent.key` names it
 * @returns {HTMLElement | null}
 */
export const next = (dot, key) => {
  const ruler = dot.closest(".ruler");
  if (!ruler) {
    return null;
  }
  const dots = /** @type {HTMLElement[]} */ ([...ruler.querySelectorAll("a.dot")]);
  const here = dots.indexOf(/** @type {HTMLElement} */ (dot));
  if (here < 0) {
    return null;
  }
  switch (key) {
    case "ArrowLeft":
      return dots[Math.max(here - 1, 0)] ?? null;
    case "ArrowRight":
      return dots[Math.min(here + 1, dots.length - 1)] ?? null;
    case "Home":
      return dots[0] ?? null;
    case "End":
      return dots.at(-1) ?? null;
    default:
      return null;
  }
};

document.addEventListener("keydown", (event) => {
  const dot = event.target instanceof Element ? event.target.closest(".ruler a.dot") : null;
  const to = dot && next(dot, event.key);
  if (to) {
    event.preventDefault();
    to.focus();
  }
});
