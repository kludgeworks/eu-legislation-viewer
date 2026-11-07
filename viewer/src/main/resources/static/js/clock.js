// SPDX-License-Identifier: MIT
// @ts-check
/**
 * @file The clock and the asides of the preparing page, catalogue/preparing/progress.jte.
 *
 * Each `time[data-elapsed]` element shows how long the preparation took. The server sets the
 * seconds when it renders the page. This script counts up from that number every second. It uses
 * the number from the server, not the clock of the browser, so a wrong browser clock does not show.
 *
 * While the act waits, a light aside shows below the plain line. Every four seconds, this script
 * shows another aside, selected at random. If the reader asks for reduced motion, the aside
 * changes without a fade.
 */

/** How long the fade of an aside takes, in milliseconds. The stylesheet uses the same time. */
export const FADE = 400;

/**
 * Pads a number to two digits, for example 4 to "04".
 *
 * @param {number} n
 * @returns {string}
 */
const pad = (n) => String(n).padStart(2, "0");

/**
 * Formats a duration as m:ss, for example 4:12. From one hour, the format is h:mm:ss, for example
 * 1:04:12.
 *
 * @param {number} seconds the duration, in whole seconds
 * @returns {string}
 */
export const elapsed = (seconds) => {
  const h = Math.floor(seconds / 3600);
  const m = Math.floor((seconds % 3600) / 60);
  const s = seconds % 60;
  return h > 0 ? `${h}:${pad(m)}:${pad(s)}` : `${m}:${pad(s)}`;
};

/** When this script first saw each clock, in milliseconds. */
const seen = /** @type {WeakMap<Element, number>} */ (new WeakMap());

/**
 * Sets each clock in `root` to the seconds from the server plus the time since the clock appeared.
 *
 * @param {ParentNode} root
 * @param {number} now the time now, in milliseconds
 */
export const tick = (root, now) => {
  for (const time of root.querySelectorAll("time[data-elapsed]")) {
    if (!(time instanceof HTMLElement)) {
      continue;
    }
    const since = seen.get(time) ?? now;
    seen.set(time, since);
    time.textContent = elapsed(
      Number(time.dataset.elapsed) + Math.floor((now - since) / 1000),
    );
  }
};

/**
 * Returns an aside that is not the one shown now, selected at random. If there is no other aside,
 * it returns the one shown.
 *
 * @param {string[]} lines all the asides
 * @param {string} shown the aside shown now
 * @param {() => number} random a number from 0 up to 1, as Math.random returns
 * @returns {string}
 */
export const next = (lines, shown, random) => {
  const others = lines.filter((line) => line !== shown);
  return others[Math.floor(random() * others.length)] ?? shown;
};

/**
 * Shows the next aside in each `[data-asides]` element of `root`. The element holds the asides in
 * `data-asides`, one on each line.
 *
 * @param {ParentNode} root
 * @param {boolean} still true if the reader asks for reduced motion: the aside changes at once
 * @param {() => number} random a number from 0 up to 1, as Math.random returns
 */
export const turn = (root, still, random) => {
  for (const aside of root.querySelectorAll("[data-asides]")) {
    if (!(aside instanceof HTMLElement)) {
      continue;
    }
    const line = next((aside.dataset.asides ?? "").split("\n"), aside.textContent ?? "", random);
    if (still) {
      aside.textContent = line;
      continue;
    }
    aside.classList.add("turning");
    setTimeout(() => {
      aside.textContent = line;
      aside.classList.remove("turning");
    }, FADE);
  }
};

const reduced = window.matchMedia("(prefers-reduced-motion: reduce)");
setInterval(() => tick(document, Date.now()), 1000);
setInterval(() => turn(document, reduced.matches, Math.random), 4000);
