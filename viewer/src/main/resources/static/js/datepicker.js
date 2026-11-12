// SPDX-License-Identifier: MIT
// @ts-check
/**
 * @file The date picker of the Reading card and of each Compare card (catalogue/reading.jte,
 * catalogue/compare/cards.jte).
 *
 * A `<date-picker>` holds a GET form with a date box and a Go button, which work without scripts.
 * `data-starts` lists the days that a new version of the act starts: ISO dates in order, separated
 * by spaces. The element hides the box and the button (class `ready`) and shows the chosen day as a
 * button. That
 * button opens a calendar of one month:
 *
 * - any day can be picked; picking a day sends the form at once
 * - the days a new version starts are ringed
 * - the days of the version that the chosen day falls in are shaded
 * - "‹ previous change" and "next change ›" pick the ringed day before and after the chosen day
 *
 * In the calendar, the arrow keys move by a day and a week, Page Up and Page Down by a month, and
 * Escape closes it.
 */

/** One day, in milliseconds. */
const DAY = 86_400_000;

/**
 * A day of the calendar.
 *
 * @typedef {object} Cell
 * @property {string} day the day, as an ISO date
 * @property {number} number the day of the month
 * @property {boolean} start whether a new version starts on the day
 * @property {boolean} shaded whether the day is in the version that the chosen day falls in
 * @property {boolean} chosen whether the day is the chosen day
 * @property {boolean} before whether the day is before the first version
 */

/**
 * Returns the ISO date that is `days` after `day`.
 *
 * @param {string} day an ISO date
 * @param {number} days
 * @returns {string}
 */
export const plus = (day, days) =>
  new Date(Date.parse(`${day}T00:00:00Z`) + days * DAY).toISOString().slice(0, 10);

/**
 * Returns the first day of the month `months` after the month of `day`.
 *
 * @param {string} day an ISO date
 * @param {number} months
 * @returns {string}
 */
export const addMonths = (day, months) => {
  const date = new Date(`${day.slice(0, 7)}-01T00:00:00Z`);
  date.setUTCMonth(date.getUTCMonth() + months);
  return date.toISOString().slice(0, 10);
};

/**
 * Returns the day that the version which applies on `day` starts, or null before the first one.
 *
 * @param {readonly string[]} starts the days that a new version starts, in order
 * @param {string} day an ISO date
 * @returns {string | null}
 */
export const versionOf = (starts, day) => starts.findLast((start) => start <= day) ?? null;

/**
 * Returns the ringed days either side of `day`: the start before it and the start after it.
 *
 * @param {readonly string[]} starts the days that a new version starts, in order
 * @param {string} day an ISO date
 * @returns {{previous: string | null, next: string | null}}
 */
export const around = (starts, day) => ({
  previous: starts.findLast((start) => start < day) ?? null,
  next: starts.find((start) => start > day) ?? null,
});

/**
 * Returns the days of the month of `shown`, and the number of empty cells before the first, so
 * that the weeks start on Monday.
 *
 * @param {string} shown an ISO date in the month
 * @param {readonly string[]} starts the days that a new version starts, in order
 * @param {string} chosen the chosen day, as an ISO date
 * @returns {{lead: number, cells: Cell[]}}
 */
export const month = (shown, starts, chosen) => {
  const first = `${shown.slice(0, 7)}-01`;
  const next = addMonths(first, 1);
  const version = versionOf(starts, chosen);
  const ringed = new Set(starts);
  /** @type {Cell[]} */
  const cells = [];
  for (let day = first; day < next; day = plus(day, 1)) {
    const own = versionOf(starts, day);
    cells.push({
      day,
      number: Number(day.slice(8)),
      start: ringed.has(day),
      shaded: own !== null && own === version,
      chosen: day === chosen,
      before: own === null,
    });
  }
  return { lead: (new Date(`${first}T00:00:00Z`).getUTCDay() + 6) % 7, cells };
};

/** How many days each key moves the focus in the calendar. */
const MOVES = new Map([
  ["ArrowLeft", -1],
  ["ArrowRight", 1],
  ["ArrowUp", -7],
  ["ArrowDown", 7],
]);

/**
 * Returns a formatter of dates in the language of the page. English dates are British, day before
 * month, as the server writes them ("10 Feb 2026").
 *
 * @param {Intl.DateTimeFormatOptions} options
 * @returns {Intl.DateTimeFormat}
 */
const format = (options) => {
  const language = document.documentElement.lang || "en";
  return new Intl.DateTimeFormat(language === "en" ? "en-GB" : language, {
    ...options,
    timeZone: "UTC",
  });
};

/**
 * Returns `day` as a date for a formatter.
 *
 * @param {string} day an ISO date
 * @returns {Date}
 */
const date = (day) => new Date(`${day}T00:00:00Z`);

/**
 * Returns a new element.
 *
 * @template {keyof HTMLElementTagNameMap} K
 * @param {K} tag
 * @param {Record<string, string>} attributes
 * @param {string} text
 * @returns {HTMLElementTagNameMap[K]}
 */
const element = (tag, attributes = {}, text = "") => {
  const made = document.createElement(tag);
  for (const [name, value] of Object.entries(attributes)) {
    made.setAttribute(name, value);
  }
  made.textContent = text;
  return made;
};

/**
 * Custom element that turns a date form into a calendar that knows the act's versions.
 */
export class DatePicker extends HTMLElement {
  /** @type {HTMLFormElement | null} */
  #form = null;
  /** @type {HTMLInputElement | null} */
  #input = null;
  /** @type {HTMLButtonElement | null} */
  #button = null;
  /** @type {HTMLElement | null} */
  #calendar = null;
  /** The first day of the month that the calendar shows. */
  #shown = "";
  /** The day that has the focus in the calendar. */
  #focus = "";

  /** Waits for the form while the parser has not added it yet. */
  #waiting = new MutationObserver(() => {
    if (this.#setUp()) {
      this.#waiting.disconnect();
    }
  });

  connectedCallback() {
    if (!this.#setUp()) {
      this.#waiting.observe(this, { childList: true, subtree: true });
    }
    document.addEventListener("pointerdown", this.#outside);
  }

  disconnectedCallback() {
    this.#waiting.disconnect();
    document.removeEventListener("pointerdown", this.#outside);
  }

  /**
   * Replaces the date box and Go with the button that opens the calendar.
   *
   * @returns {boolean} whether the form is there, and the button with it
   */
  #setUp() {
    if (this.#button) {
      return true;
    }
    const form = this.querySelector("form");
    const input = form?.querySelector('input[type="date"]');
    if (!form || !(input instanceof HTMLInputElement)) {
      return false;
    }
    this.#form = form;
    this.#input = input;
    // The class hides the box and Go. htmx settles a swapped element with an id by giving it its
    // own attributes back, so an attribute on the box would not stay.
    this.classList.add("ready");
    const button = element("button", {
      type: "button",
      class: "date-button",
      id: `${input.id}-pick`,
      "aria-haspopup": "dialog",
      "aria-expanded": "false",
    });
    input.after(button);
    form.querySelector(`label[for="${input.id}"]`)?.setAttribute("for", button.id);
    button.addEventListener("click", () => (this.#calendar ? this.close() : this.open()));
    this.#button = button;
    this.#label();
    return true;
  }

  /** The days that a new version starts, in order. */
  get starts() {
    return (this.dataset.starts ?? "").split(" ").filter(Boolean);
  }

  /** The chosen day, as an ISO date. */
  get chosen() {
    return this.#input?.value ?? "";
  }

  /** Opens the calendar at the month of the chosen day, with the focus on that day. */
  open() {
    if (!this.#button || this.#calendar) {
      return;
    }
    this.#focus = this.chosen || new Date().toISOString().slice(0, 10);
    this.#shown = this.#focus;
    this.#calendar = element("div", {
      class: "calendar",
      role: "dialog",
      "aria-label": "Pick a day",
    });
    this.#calendar.addEventListener("click", this.#click);
    this.#calendar.addEventListener("keydown", this.#key);
    this.append(this.#calendar);
    this.#button.setAttribute("aria-expanded", "true");
    this.#render(true);
  }

  /**
   * Closes the calendar.
   *
   * @param {boolean} refocus whether to give the focus back to the button
   */
  close(refocus = false) {
    this.#calendar?.remove();
    this.#calendar = null;
    this.#button?.setAttribute("aria-expanded", "false");
    if (refocus) {
      this.#button?.focus();
    }
  }

  /**
   * Picks `day`: the form asks for the law on it at once.
   *
   * @param {string} day an ISO date
   */
  pick(day) {
    if (!this.#form || !this.#input) {
      return;
    }
    this.#input.value = day;
    this.#label();
    this.close();
    this.#form.requestSubmit();
  }

  #label() {
    if (this.#button && this.chosen) {
      this.#button.textContent = `${format({ day: "numeric", month: "short", year: "numeric" }).format(date(this.chosen))} ▾`;
    }
  }

  /**
   * Draws the month that the calendar shows.
   *
   * @param {boolean} focus whether to move the focus to the day that has it
   */
  #render(focus) {
    const calendar = this.#calendar;
    if (!calendar) {
      return;
    }
    const starts = this.starts;
    const { lead, cells } = month(this.#shown, starts, this.chosen);
    const long = format({ day: "numeric", month: "long", year: "numeric" });
    const head = element("div", { class: "calendar-head" });
    head.append(
      element("button", { type: "button", "data-month": "-1", "aria-label": "Previous month" }, "‹"),
      element("b", { "aria-live": "polite" }, format({ month: "long", year: "numeric" }).format(date(this.#shown))),
      element("button", { type: "button", "data-month": "1", "aria-label": "Next month" }, "›"),
    );
    const grid = element("div", { class: "calendar-days", role: "grid" });
    const weekdays = format({ weekday: "short" });
    for (let day = 0; day < 7; day++) {
      // 5 January 1970 was a Monday.
      grid.append(element("span", { class: "weekday", "aria-hidden": "true" }, weekdays.format(new Date((4 + day) * DAY))));
    }
    for (let empty = 0; empty < lead; empty++) {
      grid.append(element("span"));
    }
    for (const cell of cells) {
      const says =
        long.format(date(cell.day)) +
        (cell.start ? " · a new version starts" : "") +
        (cell.before ? " · before the first version" : "");
      const wrap = element("span", {
        class: [cell.shaded && "shaded", cell.start && "start", cell.chosen && "chosen", cell.before && "before"]
          .filter(Boolean)
          .join(" "),
      });
      const button = element(
        "button",
        {
          type: "button",
          "data-day": cell.day,
          title: says,
          "aria-label": says,
          tabindex: cell.day === this.#focus ? "0" : "-1",
        },
        String(cell.number),
      );
      if (cell.chosen) {
        button.setAttribute("aria-current", "date");
      }
      wrap.append(button);
      grid.append(wrap);
    }
    const key = element("div", { class: "calendar-key" });
    key.append(
      element("span", { class: "key-start" }, "a new version starts"),
      element("span", { class: "key-shaded" }, "the version that applies"),
    );
    const { previous, next } = around(starts, this.chosen);
    const steps = element("div", { class: "calendar-steps" });
    for (const [day, text] of /** @type {const} */ ([
      [previous, "‹ previous change"],
      [next, "next change ›"],
    ])) {
      const step = element("button", { type: "button" }, text);
      if (day) {
        step.dataset.day = day;
      } else {
        step.disabled = true;
      }
      steps.append(step);
    }
    calendar.replaceChildren(head, grid, key, steps);
    if (focus) {
      /** @type {HTMLElement | null} */ (grid.querySelector('[tabindex="0"]'))?.focus();
    }
  }

  /**
   * Moves the focus to `day`, turning the month if it is in another.
   *
   * @param {string} day an ISO date
   */
  #move(day) {
    this.#focus = day;
    this.#shown = day;
    this.#render(true);
  }

  /** @param {MouseEvent} event */
  #click = (event) => {
    const button = event.target instanceof Element ? event.target.closest("button") : null;
    if (!button) {
      return;
    }
    if (button.dataset.day) {
      this.pick(button.dataset.day);
    } else if (button.dataset.month) {
      this.#shown = addMonths(this.#shown, Number(button.dataset.month));
      this.#focus = this.#shown;
      this.#render(false);
    }
  };

  /** @param {KeyboardEvent} event */
  #key = (event) => {
    if (event.key === "Escape") {
      event.preventDefault();
      this.close(true);
      return;
    }
    const day = event.target instanceof HTMLElement ? event.target.dataset.day : undefined;
    if (!day || !(event.target instanceof HTMLElement) || !event.target.closest(".calendar-days")) {
      return;
    }
    const by = MOVES.get(event.key);
    if (by !== undefined) {
      event.preventDefault();
      this.#move(plus(day, by));
    } else if (event.key === "PageUp" || event.key === "PageDown") {
      event.preventDefault();
      const to = addMonths(day, event.key === "PageUp" ? -1 : 1);
      const last = plus(addMonths(to, 1), -1);
      const wanted = `${to.slice(0, 8)}${day.slice(8)}`;
      this.#move(wanted > last ? last : wanted);
    }
  };

  /** @param {PointerEvent} event */
  #outside = (event) => {
    if (this.#calendar && event.target instanceof Node && !this.contains(event.target)) {
      this.close();
    }
  };
}

if (!customElements.get("date-picker")) {
  customElements.define("date-picker", DatePicker);
}
