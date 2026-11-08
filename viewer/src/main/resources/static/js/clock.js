// SPDX-License-Identifier: MIT
// @ts-check
/**
 * @file The clock and the asides of the preparing page, catalogue/preparing/progress.jte.
 *
 * Each `<elapsed-time>` element shows how long the preparation took. The server sets the
 * seconds when it renders the page. The element counts up from that number every second. It uses
 * the number from the server, not the clock of the browser, so a wrong browser clock does not show.
 *
 * While the act waits, `<rotating-aside>` shows a light aside below the plain line. Every four
 * seconds, the element shows another aside, selected at random. If the reader asks for reduced
 * motion, the aside changes without a fade.
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

/**
 * Shows elapsed time counting up each second.
 *
 * The element reads the initial duration in whole seconds from its `seconds` attribute. It starts
 * its timer when connected to the document, and clears it when disconnected.
 */
export class ElapsedTime extends HTMLElement {
  static observedAttributes = ["seconds"];

  /** @type {number | undefined} */
  #timer;

  /** @type {number | undefined} */
  #seen;

  /**
   * The duration in whole seconds.
   *
   * @returns {number}
   */
  get seconds() {
    return Number(this.getAttribute("seconds") || "0");
  }

  /**
   * @param {number} value
   */
  set seconds(value) {
    this.setAttribute("seconds", String(value));
  }

  connectedCallback() {
    this.#seen ??= Date.now();
    clearInterval(this.#timer);
    this.#timer = window.setInterval(() => this.tick(), 1000);
  }

  disconnectedCallback() {
    clearInterval(this.#timer);
    this.#timer = undefined;
  }

  /**
   * @param {string} name
   * @param {string | null} oldValue
   * @param {string | null} newValue
   */
  attributeChangedCallback(name, oldValue, newValue) {
    if (name === "seconds" && oldValue !== newValue && this.isConnected) {
      this.#seen = Date.now();
      this.tick();
    }
  }

  /**
   * Sets the content to the seconds from the server plus the time since the element appeared.
   *
   * @param {number} [now] the time now, in milliseconds
   */
  tick(now = Date.now()) {
    const since = this.#seen ?? now;
    const total = this.seconds + Math.floor((now - since) / 1000);
    const time = this.querySelector("time");
    if (time) {
      time.textContent = elapsed(total);
      time.setAttribute("datetime", `PT${total}S`);
    } else {
      this.textContent = elapsed(total);
    }
  }
}

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
 * Rotates through light asides every four seconds.
 *
 * The element reads the asides from its `asides` attribute, one on each line. It starts its timer
 * when connected to the document, and clears it when disconnected.
 */
export class RotatingAside extends HTMLElement {
  /** @type {number | undefined} */
  #timer;

  /** @type {number | undefined} */
  #fadeTimer;

  /** @type {MediaQueryList | undefined} */
  #reduced;

  connectedCallback() {
    this.#reduced = window.matchMedia("(prefers-reduced-motion: reduce)");
    clearInterval(this.#timer);
    this.#timer = window.setInterval(() => {
      this.turn(this.#reduced?.matches ?? false, Math.random);
    }, 4000);
  }

  disconnectedCallback() {
    clearInterval(this.#timer);
    clearTimeout(this.#fadeTimer);
    this.#timer = undefined;
    this.#fadeTimer = undefined;
    this.#reduced = undefined;
  }

  /**
   * Shows the next aside, selected at random.
   *
   * @param {boolean} still true if the reader asks for reduced motion: the aside changes at once
   * @param {() => number} [random] a number from 0 up to 1, as Math.random returns
   */
  turn(still, random = Math.random) {
    const raw = this.getAttribute("asides") ?? "";
    const lines = raw.split("\n");
    const line = next(lines, /** @type {HTMLElement} */ (this).textContent ?? "", random);
    if (still) {
      this.textContent = line;
      return;
    }
    this.classList.add("turning");
    clearTimeout(this.#fadeTimer);
    this.#fadeTimer = window.setTimeout(() => {
      this.textContent = line;
      this.classList.remove("turning");
      this.#fadeTimer = undefined;
    }, FADE);
  }
}

if (!customElements.get("elapsed-time")) {
  customElements.define("elapsed-time", ElapsedTime);
}

if (!customElements.get("rotating-aside")) {
  customElements.define("rotating-aside", RotatingAside);
}
