// SPDX-License-Identifier: MIT
// @ts-check
/**
 * @file The wait on an act's page, while a boosted link or form loads the next page into `#page`.
 *
 * When the request starts, `#page` gets the class `loading`, and the link or button that started
 * it gets `pressed`. CSS then shows a bar along the top of `#page` from 300ms, dims the page, and
 * from 1s shows the shape of the view that is coming in place of the text. The view is the
 * `data-view` of the link, or of the nearest element above it that has one: `#page` has its own.
 * The shape comes from `<template id="skeleton-{view}">` in the page. The text area is
 * `aria-busy`, and a polite status line says that the page is loading.
 *
 * When the request ends, htmx has swapped in the new `#page`, which has none of this. If it failed,
 * the page is put back as it was.
 *
 * A page opened at a fragment, such as a Read mark's link to a unit in Compare, often loads that
 * unit after the browser looked for it. When a swap brings the fragment's element, the page scrolls
 * to it, once.
 *
 * A jump to a fragment lands on its element exactly. The browser lays out the tables of a text and
 * the differences of a comparison only near the screen (content-visibility), and guesses the height
 * of the others. Thus its own jump can stop short of the element or pass it. The page scrolls to
 * the element again until it stays where it is.
 */

/**
 * Returns the view that a request from `source` opens: "read", "changes", "whole" or "synopsis".
 *
 * @param {Element} source the link or form that started the request
 * @returns {string}
 */
export const coming = (source) =>
  source.closest("[data-view]")?.getAttribute("data-view") ?? "read";

/**
 * Marks `page` as loading the page that `source` asks for.
 *
 * @param {HTMLElement} page the `#page` element
 * @param {Element} source the link or form that started the request
 */
export const start = (page, source) => {
  const view = coming(source);
  page.classList.add("loading");
  page.dataset.coming = view;
  source.classList.add("pressed");
  const text = page.querySelector("#text");
  const shape = page.querySelector(`template#skeleton-${view}`);
  if (text) {
    text.setAttribute("aria-busy", "true");
    if (shape instanceof HTMLTemplateElement) {
      text.prepend(shape.content.cloneNode(true));
    }
  }
  const status = page.querySelector(".page-status");
  if (status) {
    status.textContent = "Loading…";
  }
};

/**
 * Puts `page` back as it was before {@link start}.
 *
 * @param {HTMLElement} page the `#page` element
 */
export const stop = (page) => {
  page.classList.remove("loading");
  delete page.dataset.coming;
  for (const pressed of page.querySelectorAll(".pressed")) {
    pressed.classList.remove("pressed");
  }
  for (const shape of page.querySelectorAll(".skeleton-over")) {
    shape.remove();
  }
  page.querySelector("#text")?.removeAttribute("aria-busy");
  const status = page.querySelector(".page-status");
  if (status) {
    status.textContent = "";
  }
};

/**
 * Returns `#page` if a request from `source` loads the next page into it: the request is boosted,
 * and `source` is in `#page`. Otherwise it returns null. The slots of the page load with their own
 * requests, which are not boosted.
 *
 * @param {Event} event an htmx request event
 * @returns {HTMLElement | null}
 */
export const pageOf = (event) => {
  const ctx = /** @type {CustomEvent} */ (event).detail?.ctx;
  const page = document.getElementById("page");
  const source = ctx?.sourceElement;
  return page && source instanceof Element && ctx.request?.headers?.["HX-Boosted"] && page.contains(source)
    ? page
    : null;
};

document.addEventListener("htmx:before:request", (event) => {
  const page = pageOf(event);
  if (page) {
    start(page, /** @type {CustomEvent} */ (event).detail.ctx.sourceElement);
  }
});

document.addEventListener("htmx:finally:request", () => {
  const page = document.getElementById("page");
  if (page?.classList.contains("loading")) {
    stop(page);
  }
});

/** How many frames a jump waits, at most, for its element to stay where it is. */
const FRAMES = 10;

/**
 * Scrolls to `target`, and again in each frame after it, until `target` stays where it is: what
 * the browser lays out when `target` comes near the screen can move it.
 *
 * @param {Element} target
 */
export const land = (target) => {
  /** @type {number | null} */
  let last = null;
  let frames = 0;
  const again = () => {
    const top = target.getBoundingClientRect().top;
    if (top === last || frames++ === FRAMES) {
      return;
    }
    last = top;
    target.scrollIntoView({ block: "start" });
    requestAnimationFrame(again);
  };
  target.scrollIntoView({ block: "start" });
  requestAnimationFrame(again);
};

/** Whether the page still has to scroll to the element of its fragment. */
let arriving = location.hash.length > 1;

/**
 * Scrolls to the element of the page's fragment, if it is there now. Returns whether it was.
 *
 * @returns {boolean}
 */
export const arrive = () => {
  const target = document.getElementById(decodeURIComponent(location.hash.slice(1)));
  if (target) {
    land(target);
  }
  return target !== null;
};

document.addEventListener("htmx:after:swap", () => {
  if (arriving && arrive()) {
    arriving = false;
  }
});

// a link within the page, for example an entry of the outline
window.addEventListener("hashchange", arrive);
