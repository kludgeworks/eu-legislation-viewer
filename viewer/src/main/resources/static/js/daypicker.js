// SPDX-License-Identifier: MIT
// @ts-check
/**
 * @file The day control of a page (catalogue/day.jte): one field that sets the day of the page, or
 * its two days.
 *
 * A `<day-picker>` holds a GET form with date boxes and a Go button, which work without scripts.
 * The element hides them (class `ready`) and shows one text field and a button that opens a
 * calendar of one month.
 *
 * - `data-single` is the address that takes one day, in the parameter `on`.
 * - `data-range` is the address that takes two days, in the parameters `from` and `to`. Without
 *   it, the page takes one day only, and the calendar has no switch.
 * - `data-changes` lists the days with a change: `2021-03-11=words|words;2021-12-31=words`.
 *
 * With one day, the reader types a day in the field, or picks a day in the calendar. Each sends
 * the form at once. With two days, the calendar has a box for each day. A click on a day sets the
 * box that has the frame, and the frame moves to the other box. "Show" sends the form.
 *
 * The calendar puts a dot on each day with a change. The hover of such a day shows what changes on
 * it, and so does its focus when the reader moves with the keys. "Previous change" and "Next change" move the focus to the nearest day with a
 * dot. With two days, they also set the box that has the frame to that day, and the frame stays. The
 * days between two days have no shade.
 *
 * In the calendar, the arrow keys move by a day and a week, Page Up and Page Down by a month, and
 * Escape closes it.
 */

/** One day, in milliseconds. */
const DAY = 86_400_000;

/** How many acts the summary of a day names. */
const NAMED = 3;

const MONTHS = [
  "january",
  "february",
  "march",
  "april",
  "may",
  "june",
  "july",
  "august",
  "september",
  "october",
  "november",
  "december",
];

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
 * Returns the days of the month of `shown`, and the number of empty cells before the first, so
 * that the weeks start on Monday.
 *
 * @param {string} shown an ISO date in the month
 * @returns {{lead: number, days: string[]}}
 */
export const month = (shown) => {
  const first = `${shown.slice(0, 7)}-01`;
  const next = addMonths(first, 1);
  /** @type {string[]} */
  const days = [];
  for (let day = first; day < next; day = plus(day, 1)) {
    days.push(day);
  }
  return { lead: (new Date(`${first}T00:00:00Z`).getUTCDay() + 6) % 7, days };
};

/**
 * Returns the days with a change, each with what changes on it, from the text of `data-changes`.
 *
 * @param {string} text for example `2021-03-11=2021/255 applies from this day`
 * @returns {Map<string, string[]>}
 */
export const changes = (text) => {
  /** @type {Map<string, string[]>} */
  const found = new Map();
  for (const entry of text.split(";")) {
    const at = entry.indexOf("=");
    if (at > 0) {
      found.set(entry.slice(0, at), entry.slice(at + 1).split("|").filter(Boolean));
    }
  }
  return found;
};

/**
 * Returns the days with a change either side of `day`.
 *
 * @param {readonly string[]} days the days with a change, in order
 * @param {string} day an ISO date
 * @returns {{previous: string | null, next: string | null}}
 */
export const around = (days, day) => ({
  previous: days.findLast((each) => each < day) ?? null,
  next: days.find((each) => each > day) ?? null,
});

/**
 * Returns what the summary of a day says: its first lines, and how many more acts it has.
 *
 * @param {readonly string[]} lines what each act does on the day
 * @returns {string[]}
 */
export const summary = (lines) => {
  const more = lines.length - NAMED;
  return more > 0
    ? [...lines.slice(0, NAMED), more === 1 ? "and 1 more act" : `and ${more} more acts`]
    : [...lines];
};

/**
 * Returns the ISO date of a day that the reader typed, or null if it is not a day. It reads
 * "18 November 2026", "18 Nov 2026", "18/11/2026", "18.11.2026" and "2026-11-18". The day comes
 * before the month, as in the dates of the pages.
 *
 * @param {string} text
 * @returns {string | null}
 */
export const parse = (text) => {
  const fields = text
    .trim()
    .toLowerCase()
    .split(/[\s./-]+/)
    .filter(Boolean);
  const [a, b, c] = fields;
  if (fields.length !== 3 || a === undefined || b === undefined || c === undefined) {
    return null;
  }
  const iso = a.length === 4;
  const year = Number(iso ? a : c);
  const day = Number(iso ? c : a);
  const named = MONTHS.findIndex((each) => b.length >= 3 && each.startsWith(b));
  const number = named >= 0 ? named + 1 : Number(b);
  if (![year, number, day].every(Number.isInteger) || year < 1000 || year > 9999) {
    return null;
  }
  const date = new Date(Date.UTC(year, number - 1, day));
  return date.getUTCFullYear() === year && date.getUTCMonth() === number - 1 && date.getUTCDate() === day
    ? date.toISOString().slice(0, 10)
    : null;
};

/** How many days each key moves the focus in the calendar. */
const MOVES = new Map([
  ["ArrowLeft", -1],
  ["ArrowRight", 1],
  ["ArrowUp", -7],
  ["ArrowDown", 7],
]);

/**
 * Returns a formatter of dates. The dates are British, day before month, as the server writes them.
 *
 * @param {Intl.DateTimeFormatOptions} options
 * @returns {Intl.DateTimeFormat}
 */
const format = (options) => new Intl.DateTimeFormat("en-GB", { ...options, timeZone: "UTC" });

/**
 * Returns `day` in words, for example "18 November 2026". Returns an empty text for no day.
 *
 * @param {string} day an ISO date, or an empty text
 * @returns {string}
 */
export const words = (day) =>
  day ? format({ day: "numeric", month: "long", year: "numeric" }).format(new Date(`${day}T00:00:00Z`)) : "";

/**
 * Returns a new element.
 *
 * @template {keyof HTMLElementTagNameMap} K
 * @param {K} name
 * @param {Record<string, string>} attributes
 * @param {string} text
 * @returns {HTMLElementTagNameMap[K]}
 */
const element = (name, attributes = {}, text = "") => {
  const made = document.createElement(name);
  for (const [attribute, value] of Object.entries(attributes)) {
    made.setAttribute(attribute, value);
  }
  made.textContent = text;
  return made;
};

/** Returns today, as an ISO date. */
const today = () => new Date().toISOString().slice(0, 10);

/**
 * Custom element that turns a date form into one field with a calendar.
 */
export class DayPicker extends HTMLElement {
  /** @type {HTMLFormElement | null} */
  #form = null;
  /** @type {HTMLInputElement | null} */
  #field = null;
  /** @type {HTMLButtonElement | null} */
  #button = null;
  /** @type {HTMLElement | null} */
  #panel = null;
  /** Whether the reader selects two days. */
  #two = false;
  /** The day of the page, or its second day. An ISO date, or empty. */
  #day = "";
  /** The first of two days. An ISO date, or empty. */
  #first = "";
  /** Which of the two days the calendar sets. */
  #end = /** @type {"first" | "second"} */ ("second");
  /** The first day of the month that the calendar shows. */
  #shown = "";
  /** The day that has the focus in the calendar. */
  #focus = "";
  /** What the reader must correct, or empty. */
  #error = "";
  /** Whether the reader moves in the calendar with the keys. A day then shows its summary on focus. */
  #keys = false;

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

  /** The days with a change, each with what changes on it. */
  get changes() {
    return changes(this.dataset.changes ?? "");
  }

  /** Whether the page takes two days. */
  get ranged() {
    return Boolean(this.dataset.range);
  }

  /**
   * Replaces the date boxes and Go with the field and the button that opens the calendar.
   *
   * @returns {boolean} whether the form is there, and the field with it
   */
  #setUp() {
    if (this.#field) {
      return true;
    }
    const form = this.querySelector("form");
    const on = form?.querySelector('input[name="on"], input[name="to"]');
    if (!form || !(on instanceof HTMLInputElement) || !form.querySelector("button")) {
      return false;
    }
    const from = form.querySelector('input[name="from"]');
    this.#form = form;
    this.#day = on.value;
    this.#first = from instanceof HTMLInputElement ? from.value : "";
    this.#two = this.#first !== "";
    // The class hides the boxes and Go. htmx settles a swapped element with an id by giving it its
    // own attributes back, so an attribute on a box would not stay.
    this.classList.add("ready");
    const field = element("input", { type: "text", class: "day-text", id: `${on.id}-text`, autocomplete: "off" });
    const button = element("button", {
      type: "button",
      class: "day-open",
      "aria-label": "Open the calendar",
      "aria-haspopup": "dialog",
      "aria-expanded": "false",
    });
    const box = element("span", { class: "day-field" });
    box.append(field, button);
    const label = form.querySelector("label");
    label?.setAttribute("for", field.id);
    (label ?? form).after(box);
    if (!label) {
      form.prepend(box);
    }
    button.addEventListener("click", () => (this.#panel ? this.close() : this.open()));
    field.addEventListener("keydown", (event) => {
      if (event.key === "Enter") {
        event.preventDefault();
        this.#typed();
      }
    });
    field.addEventListener("click", () => {
      if (this.#two && !this.#panel) {
        this.open();
      }
    });
    this.#field = field;
    this.#button = button;
    this.#write();
    return true;
  }

  /** Writes the day, or the two days, in the field. */
  #write() {
    const field = this.#field;
    if (!field) {
      return;
    }
    field.readOnly = this.#two;
    field.value = this.#two ? `${words(this.#first) || "…"} – ${words(this.#day) || "…"}` : words(this.#day);
    field.removeAttribute("aria-invalid");
  }

  /** Reads the day that the reader typed in the field, and asks for it. */
  #typed() {
    const field = this.#field;
    if (!field || this.#two) {
      return;
    }
    const day = parse(field.value);
    if (day) {
      this.#day = day;
      this.#go();
    } else {
      field.setAttribute("aria-invalid", "true");
    }
  }

  /** Opens the calendar at the month of the day, with the focus on that day. */
  open() {
    if (!this.#button || this.#panel) {
      return;
    }
    this.#focus = (this.#end === "first" ? this.#first : this.#day) || this.#day || today();
    this.#shown = this.#focus;
    this.#error = "";
    this.#panel = element("div", { class: "day-panel", role: "dialog", "aria-label": "Choose the day" });
    this.#panel.addEventListener("click", this.#click);
    this.#panel.addEventListener("keydown", this.#key);
    this.#panel.addEventListener("change", this.#change);
    this.#panel.addEventListener("focusin", this.#hover);
    this.#panel.addEventListener("pointerover", this.#hover);
    this.append(this.#panel);
    this.#button.setAttribute("aria-expanded", "true");
    this.#render("day");
  }

  /**
   * Closes the calendar.
   *
   * @param {boolean} refocus whether to give the focus back to the button
   */
  close(refocus = false) {
    this.#panel?.remove();
    this.#panel = null;
    this.#button?.setAttribute("aria-expanded", "false");
    if (refocus) {
      this.#button?.focus();
    }
  }

  /** Sends the form: one day to the address for one day, two days to the address for two. */
  #go() {
    const form = this.#form;
    if (!form) {
      return;
    }
    for (const box of form.querySelectorAll('input[name="on"], input[name="from"], input[name="to"]')) {
      box.remove();
    }
    const first = this.#first < this.#day ? this.#first : this.#day;
    const second = this.#first < this.#day ? this.#day : this.#first;
    /** @type {Record<string, string>} */
    const days = this.#two ? { from: first, to: second } : { on: this.#day };
    for (const [name, value] of Object.entries(days)) {
      form.append(element("input", { type: "hidden", name, value }));
    }
    for (const kept of form.querySelectorAll('input[type="hidden"][name="occurrence"]')) {
      if (kept instanceof HTMLInputElement) {
        kept.disabled = !this.#two;
      }
    }
    form.action = (this.#two ? this.dataset.range : this.dataset.single) ?? form.action;
    this.#write();
    this.close();
    form.requestSubmit();
  }

  /**
   * Picks `day` in the calendar. With one day, the form asks for it at once. With two days, it
   * sets the day that has the frame, and the frame moves to the other day.
   *
   * @param {string} day an ISO date
   */
  pick(day) {
    if (!this.#two) {
      this.#day = day;
      this.#go();
      return;
    }
    if (this.#end === "first") {
      this.#first = day;
      this.#end = "second";
    } else {
      this.#day = day;
      this.#end = "first";
    }
    this.#error = "";
    this.#focus = day;
    this.#write();
    this.#render("day");
  }

  /** Asks for the two days, if the reader gave two different days. */
  #show() {
    if (!this.#first || !this.#day) {
      this.#error = "Give two days.";
    } else if (this.#first === this.#day) {
      this.#error = "Give two different days.";
    } else {
      this.#go();
      return;
    }
    this.#render("none");
  }

  /**
   * Draws the calendar.
   *
   * @param {"day" | "month" | "year" | "none"} focus what gets the focus after it
   */
  #render(focus) {
    const panel = this.#panel;
    if (!panel) {
      return;
    }
    const marked = this.changes;
    const children = [];
    if (this.ranged) {
      const mode = element("div", { class: "day-mode", role: "group", "aria-label": "How many days" });
      mode.append(
        element("button", { type: "button", "data-mode": "one", "aria-pressed": String(!this.#two) }, "One day"),
        element("button", { type: "button", "data-mode": "two", "aria-pressed": String(this.#two) }, "Compare two days"),
      );
      children.push(mode);
    }
    if (this.#two) {
      const ends = element("div", { class: "day-ends" });
      for (const [end, text, value] of /** @type {const} */ ([
        ["first", "First day", this.#first],
        ["second", "Second day", this.#day],
      ])) {
        const label = element("label", { class: end === this.#end ? "active" : "" });
        label.append(
          element("span", {}, text),
          element("input", { type: "text", "data-end": end, value: words(value), autocomplete: "off" }),
        );
        ends.append(label);
      }
      children.push(ends);
    }
    const head = element("div", { class: "day-head" });
    const months = element("select", { "aria-label": "Month", "data-pick": "month" });
    const name = format({ month: "long" });
    for (let each = 0; each < 12; each++) {
      const option = element("option", { value: String(each) }, name.format(new Date(Date.UTC(2000, each, 1))));
      option.selected = each === Number(this.#shown.slice(5, 7)) - 1;
      months.append(option);
    }
    const years = element("select", { "aria-label": "Year", "data-pick": "year" });
    const days = [...marked.keys()];
    const shownYear = Number(this.#shown.slice(0, 4));
    const firstYear = Math.min(shownYear, Number((days[0] ?? this.#shown).slice(0, 4))) - 1;
    const lastYear = Math.max(shownYear, Number((days.at(-1) ?? this.#shown).slice(0, 4)), new Date().getUTCFullYear()) + 1;
    for (let each = firstYear; each <= lastYear; each++) {
      const option = element("option", { value: String(each) }, String(each));
      option.selected = each === shownYear;
      years.append(option);
    }
    head.append(
      element("button", { type: "button", "data-month": "-1", "aria-label": "Previous month" }, "‹"),
      months,
      years,
      element("button", { type: "button", "data-month": "1", "aria-label": "Next month" }, "›"),
    );
    const grid = element("div", { class: "day-grid", role: "grid" });
    const weekdays = format({ weekday: "short" });
    for (let day = 0; day < 7; day++) {
      // 5 January 1970 was a Monday.
      grid.append(element("span", { class: "weekday", "aria-hidden": "true" }, weekdays.format(new Date((4 + day) * DAY))));
    }
    const { lead, days: cells } = month(this.#shown);
    for (let empty = 0; empty < lead; empty++) {
      grid.append(element("span"));
    }
    for (const day of cells) {
      const lines = marked.get(day) ?? [];
      const chosen = day === this.#day;
      const other = this.#two && day === this.#first;
      const button = element(
        "button",
        {
          type: "button",
          class: [chosen && "chosen", other && "other", lines.length > 0 && "dot"].filter(Boolean).join(" "),
          "data-day": day,
          "aria-label": [words(day), ...summary(lines)].join(". "),
          tabindex: day === this.#focus ? "0" : "-1",
        },
        String(Number(day.slice(8))),
      );
      if (chosen) {
        button.setAttribute("aria-current", "date");
      }
      grid.append(button);
    }
    const tip = element("div", { class: "day-tip", "aria-hidden": "true", hidden: "" });
    const wrap = element("div", { class: "day-days" });
    wrap.append(grid, tip);
    const foot = element("div", { class: "day-foot" });
    foot.append(
      element("button", { type: "button", class: "link", "data-direction": "previous" }, "‹ Previous change"),
      element("button", { type: "button", class: "link", "data-direction": "next" }, "Next change ›"),
    );
    this.#moves(foot);
    foot.append(
      this.#two
        ? element("button", { type: "button", class: "day-show", "data-show": "" }, "Show")
        : element("button", { type: "button", class: "link", "data-day": today() }, "Today"),
    );
    children.push(head, wrap, foot);
    if (this.#error) {
      children.push(element("p", { class: "day-error", role: "alert" }, this.#error));
    }
    panel.replaceChildren(...children);
    const target =
      focus === "day"
        ? grid.querySelector('[tabindex="0"]')
        : focus === "none"
          ? null
          : panel.querySelector(`[data-pick="${focus}"]`);
    if (target instanceof HTMLElement) {
      target.focus();
    }
  }

  /**
   * Sets the days of "Previous change" and "Next change" in `foot`. They count from the day of the
   * focus, and with two days from the day of the box that has the frame, if it has a day.
   *
   * @param {ParentNode | null | undefined} foot
   */
  #moves(foot) {
    const from = (this.#two && (this.#end === "first" ? this.#first : this.#day)) || this.#focus;
    const nearest = around([...this.changes.keys()], from);
    for (const button of foot?.querySelectorAll("[data-direction]") ?? []) {
      if (!(button instanceof HTMLButtonElement)) {
        continue;
      }
      const day = button.dataset.direction === "previous" ? nearest.previous : nearest.next;
      button.disabled = !day;
      if (day) {
        button.dataset.move = day;
      } else {
        delete button.dataset.move;
      }
    }
  }

  /**
   * Moves the focus to `day`, turning the month if it is in another. With two days, `day` is also
   * the day that has the frame, and the frame stays on it: the reader can move on from change to
   * change.
   *
   * @param {string} day an ISO date
   */
  #move(day) {
    if (this.#two) {
      if (this.#end === "first") {
        this.#first = day;
      } else {
        this.#day = day;
      }
      this.#error = "";
      this.#write();
    }
    this.#focus = day;
    this.#shown = day;
    this.#render("day");
  }

  /** @param {MouseEvent} event */
  #click = (event) => {
    this.#keys = false;
    const button = event.target instanceof Element ? event.target.closest("button") : null;
    if (!button) {
      return;
    }
    if (button.dataset.day) {
      this.pick(button.dataset.day);
    } else if (button.dataset.move) {
      this.#move(button.dataset.move);
    } else if (button.dataset.month) {
      this.#shown = addMonths(this.#shown, Number(button.dataset.month));
      this.#focus = this.#shown;
      this.#render("none");
    } else if (button.dataset.mode) {
      this.#two = button.dataset.mode === "two";
      this.#first = "";
      this.#end = this.#two ? "first" : "second";
      this.#error = "";
      this.#write();
      this.#render("none");
    } else if (button.dataset.show !== undefined) {
      this.#show();
    }
  };

  /** @param {Event} event */
  #change = (event) => {
    const target = event.target;
    if (target instanceof HTMLSelectElement) {
      const year = target.dataset.pick === "year" ? target.value : this.#shown.slice(0, 4);
      const index = target.dataset.pick === "month" ? Number(target.value) : Number(this.#shown.slice(5, 7)) - 1;
      this.#shown = `${year}-${String(index + 1).padStart(2, "0")}-01`;
      this.#focus = this.#shown;
      this.#render(target.dataset.pick === "year" ? "year" : "month");
    } else if (target instanceof HTMLInputElement && target.dataset.end) {
      const day = parse(target.value);
      if (!day) {
        target.setAttribute("aria-invalid", "true");
        return;
      }
      if (target.dataset.end === "first") {
        this.#first = day;
      } else {
        this.#day = day;
      }
      this.#error = "";
      this.#shown = day;
      this.#focus = day;
      this.#write();
      this.#render("none");
    }
  };

  /**
   * Shows what changes on the day under the pointer, or on the day that has the focus.
   *
   * @param {Event} event
   */
  #hover = (event) => {
    const panel = this.#panel;
    const tip = panel?.querySelector(".day-tip");
    if (!panel || !(tip instanceof HTMLElement) || !(event.target instanceof Element)) {
      return;
    }
    const box = event.target.closest("[data-end]");
    if (box instanceof HTMLInputElement && (box.dataset.end === "first" || box.dataset.end === "second")) {
      this.#end = box.dataset.end;
      for (const label of panel.querySelectorAll(".day-ends label")) {
        label.classList.toggle("active", label.contains(box));
      }
      this.#moves(panel.querySelector(".day-foot"));
    }
    const button = event.target.closest(".day-grid button");
    if (event.type === "focusin" && !this.#keys) {
      return;
    }
    const lines = button instanceof HTMLElement ? (this.changes.get(button.dataset.day ?? "") ?? []) : [];
    if (!(button instanceof HTMLElement) || lines.length === 0) {
      tip.hidden = true;
      return;
    }
    tip.replaceChildren(
      element("b", {}, words(button.dataset.day ?? "")),
      ...summary(lines).map((line) => element("span", {}, line)),
    );
    tip.style.top = `${button.offsetTop + button.offsetHeight + 4}px`;
    tip.style.left = `${Math.max(0, Math.min(button.offsetLeft, (tip.parentElement?.clientWidth ?? 0) - 240))}px`;
    tip.hidden = false;
  };

  /** @param {KeyboardEvent} event */
  #key = (event) => {
    if (event.key === "Escape") {
      event.preventDefault();
      this.close(true);
      return;
    }
    const target = event.target;
    if (target instanceof HTMLInputElement && target.dataset.end && event.key === "Enter") {
      event.preventDefault();
      target.dispatchEvent(new Event("change", { bubbles: true }));
      return;
    }
    const day = target instanceof HTMLElement ? target.dataset.day : undefined;
    if (!day || !(target instanceof HTMLElement) || !target.closest(".day-grid")) {
      return;
    }
    const by = MOVES.get(event.key);
    this.#keys = true;
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
    if (this.#panel && event.target instanceof Node && !this.contains(event.target)) {
      this.close();
    }
  };
}

if (!customElements.get("day-picker")) {
  customElements.define("day-picker", DayPicker);
}
