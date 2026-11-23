// SPDX-License-Identifier: MIT
// @ts-check
import { afterEach, describe, expect, test, vi } from "vitest";
import {
  addMonths,
  around,
  changes,
  month,
  summary,
  parse,
  plus,
  words,
} from "../../main/resources/static/js/daypicker.js";

const CHANGES = "2021-03-11=2021/255 applies from this day;2021-12-31=A part of 2019/103 applies from this day|X applies";

afterEach(() => {
  document.body.innerHTML = "";
});

/**
 * Returns a day control for the page of 15 March 2021, in the document.
 *
 * @param {{range?: boolean, from?: string}} options
 * @returns {{picker: HTMLElement, form: HTMLFormElement, sent: import("vitest").Mock}}
 */
const control = ({ range = true, from = "" } = {}) => {
  const boxes = from
    ? `<label for="on-from">The law on</label><input id="on-from" name="from" type="date" value="${from}">
       <label for="on">compared with</label><input id="on" name="to" type="date" value="2021-03-15">`
    : `<label for="on">The law on</label><input id="on" name="on" type="date" value="2021-03-15">`;
  // The form is in the element before the element is in the document, as on a page.
  const holder = document.createElement("div");
  holder.innerHTML = `<day-picker data-single="/history" ${range ? 'data-range="/compare"' : ""}
      data-changes="${CHANGES}"><form method="get" action="/history">${boxes}
      <input type="hidden" name="occurrence" value="art_6"><button>Go</button></form></day-picker>`;
  const picker = /** @type {HTMLElement} */ (holder.firstElementChild);
  document.body.append(picker);
  const form = /** @type {HTMLFormElement} */ (picker.querySelector("form"));
  const sent = vi.fn();
  form.requestSubmit = sent;
  return { picker, form, sent };
};

/**
 * Clicks the element that `selector` finds in `picker`.
 *
 * @param {HTMLElement} picker
 * @param {string} selector
 */
const click = (picker, selector) => {
  const found = picker.querySelector(selector);
  if (!(found instanceof HTMLElement)) {
    throw new Error(`No ${selector}`);
  }
  found.click();
};

/**
 * Returns the values that the form sends, by name.
 *
 * @param {HTMLFormElement} form
 * @returns {Record<string, string>}
 */
const values = (form) =>
  Object.fromEntries(
    [...form.querySelectorAll("input[name]")]
      .filter((box) => box instanceof HTMLInputElement && !box.disabled)
      .map((box) => [box.getAttribute("name"), /** @type {HTMLInputElement} */ (box).value]),
  );

describe("the days", () => {
  test("add up across months and years", () => {
    expect(plus("2016-02-28", 1)).toBe("2016-02-29");
    expect(plus("2016-12-31", 1)).toBe("2017-01-01");
    expect(addMonths("2016-01-31", 1)).toBe("2016-02-01");
    expect(addMonths("2016-01-15", -1)).toBe("2015-12-01");
  });

  test("of a month start their weeks on Monday", () => {
    // 1 March 2021 was a Monday, 1 November 2015 a Sunday.
    expect(month("2021-03-15").lead).toBe(0);
    expect(month("2021-03-15").days).toHaveLength(31);
    expect(month("2015-11-20").lead).toBe(6);
  });

  test("with a change come from the data of the page", () => {
    const found = changes(CHANGES);
    expect([...found.keys()]).toEqual(["2021-03-11", "2021-12-31"]);
    expect(found.get("2021-12-31")).toEqual(["A part of 2019/103 applies from this day", "X applies"]);
    expect(changes("").size).toBe(0);
  });

  test("have a change before and after, except at the ends", () => {
    const days = ["2021-03-11", "2021-12-31"];
    expect(around(days, "2021-03-15")).toEqual({ previous: "2021-03-11", next: "2021-12-31" });
    expect(around(days, "2021-03-11")).toEqual({ previous: null, next: "2021-12-31" });
    expect(around(days, "2022-01-01")).toEqual({ previous: "2021-12-31", next: null });
  });

  test("are read as the reader types them, the day before the month", () => {
    expect(parse("18 November 2026")).toBe("2026-11-18");
    expect(parse(" 18 nov 2026 ")).toBe("2026-11-18");
    expect(parse("18/11/2026")).toBe("2026-11-18");
    expect(parse("1.2.2019")).toBe("2019-02-01");
    expect(parse("2026-11-18")).toBe("2026-11-18");
    expect(parse("31 February 2021")).toBeNull();
    expect(parse("November 2026")).toBeNull();
    expect(parse("soon")).toBeNull();
  });

  test("are written in words", () => {
    expect(words("2026-11-18")).toBe("18 November 2026");
    expect(words("")).toBe("");
  });
});

describe("the summary of a day", () => {
  test("names three acts at most, and says how many more there are", () => {
    expect(summary(["a", "b"])).toEqual(["a", "b"]);
    expect(summary(["a", "b", "c", "d"])).toEqual(["a", "b", "c", "and 1 more act"]);
    expect(summary(["a", "b", "c", "d", "e", "f", "g"])).toEqual(["a", "b", "c", "and 4 more acts"]);
  });
});

describe("the day control", () => {
  test("shows one field with the day in words, and hides the date box", () => {
    const { picker } = control();
    expect(picker.classList.contains("ready")).toBe(true);
    const field = /** @type {HTMLInputElement} */ (picker.querySelector(".day-text"));
    expect(field.value).toBe("15 March 2021");
    expect(field.readOnly).toBe(false);
    expect(picker.querySelector(".day-panel")).toBeNull();
  });

  test("asks for the day that the reader types", () => {
    const { picker, form, sent } = control();
    const field = /** @type {HTMLInputElement} */ (picker.querySelector(".day-text"));
    field.value = "2 July 2020";
    field.dispatchEvent(new KeyboardEvent("keydown", { key: "Enter", bubbles: true }));
    expect(sent).toHaveBeenCalledOnce();
    expect(values(form)).toEqual({ on: "2020-07-02" });
    expect(form.action).toContain("/history");
  });

  test("marks a day that is not a day, and does not ask for it", () => {
    const { picker, sent } = control();
    const field = /** @type {HTMLInputElement} */ (picker.querySelector(".day-text"));
    field.value = "soon";
    field.dispatchEvent(new KeyboardEvent("keydown", { key: "Enter", bubbles: true }));
    expect(sent).not.toHaveBeenCalled();
    expect(field.getAttribute("aria-invalid")).toBe("true");
  });

  test("opens a calendar of the month of the day, with a dot on a day with a change", () => {
    const { picker } = control();
    click(picker, ".day-open");
    expect(picker.querySelector(".day-open")?.getAttribute("aria-expanded")).toBe("true");
    expect(picker.querySelectorAll(".day-grid button")).toHaveLength(31);
    expect(picker.querySelector(".day-grid .chosen")?.textContent).toBe("15");
    const dotted = [...picker.querySelectorAll(".day-grid .dot")];
    expect(dotted.map((each) => each.textContent)).toEqual(["11"]);
    expect(dotted[0]?.getAttribute("aria-label")).toBe("11 March 2021. 2021/255 applies from this day");
    // One day is the default. The days between two days have no shade.
    expect(picker.querySelector('[data-mode="one"]')?.getAttribute("aria-pressed")).toBe("true");
    expect(picker.querySelector(".day-ends")).toBeNull();
  });

  test("asks for a day at once when the reader picks it", () => {
    const { picker, form, sent } = control();
    click(picker, ".day-open");
    click(picker, '[data-day="2021-03-11"]');
    expect(sent).toHaveBeenCalledOnce();
    expect(values(form)).toEqual({ on: "2021-03-11" });
    expect(picker.querySelector(".day-panel")).toBeNull();
  });

  test("moves to the day of the previous change and of the next change without asking", () => {
    const { picker, sent } = control();
    click(picker, ".day-open");
    click(picker, '[data-move="2021-12-31"]');
    expect(picker.querySelector('.day-grid [tabindex="0"]')?.getAttribute("data-day")).toBe("2021-12-31");
    expect(sent).not.toHaveBeenCalled();
  });

  test("shows what changes on a day when the day gets the pointer or the focus", () => {
    const { picker } = control();
    click(picker, ".day-open");
    const tip = /** @type {HTMLElement} */ (picker.querySelector(".day-tip"));
    expect(tip.hidden).toBe(true);
    picker.querySelector('[data-day="2021-03-11"]')?.dispatchEvent(new Event("pointerover", { bubbles: true }));
    expect(tip.hidden).toBe(false);
    expect(tip.textContent).toBe("11 March 20212021/255 applies from this day");
    picker.querySelector('[data-day="2021-03-12"]')?.dispatchEvent(new Event("pointerover", { bubbles: true }));
    expect(tip.hidden).toBe(true);
  });

  test("lets the reader select two days, and asks for them only on Show", () => {
    const { picker, form, sent } = control();
    click(picker, ".day-open");
    click(picker, '[data-mode="two"]');
    expect(picker.querySelectorAll(".day-ends input")).toHaveLength(2);
    // The calendar sets the first day, then the second day.
    expect(picker.querySelector(".day-ends .active input")?.getAttribute("data-end")).toBe("first");
    click(picker, '[data-day="2021-03-02"]');
    expect(sent).not.toHaveBeenCalled();
    expect(picker.querySelector(".day-grid .other")?.textContent).toBe("2");
    expect(picker.querySelector(".day-grid .chosen")?.textContent).toBe("15");
    expect(picker.querySelector(".day-ends .active input")?.getAttribute("data-end")).toBe("second");
    click(picker, "[data-show]");
    expect(sent).toHaveBeenCalledOnce();
    expect(values(form)).toEqual({ from: "2021-03-02", to: "2021-03-15", occurrence: "art_6" });
    expect(form.action).toContain("/compare");
  });

  test("puts the earlier of two days first", () => {
    const { picker, form } = control();
    click(picker, ".day-open");
    click(picker, '[data-mode="two"]');
    click(picker, '[data-day="2021-03-20"]');
    click(picker, "[data-show]");
    expect(values(form)).toEqual({ from: "2021-03-15", to: "2021-03-20", occurrence: "art_6" });
  });

  test("puts the day of the previous or next change in the day with the frame, and keeps the frame", () => {
    const { picker, sent } = control();
    click(picker, ".day-open");
    click(picker, '[data-mode="two"]');
    const box = (/** @type {string} */ end) =>
      /** @type {HTMLInputElement} */ (picker.querySelector(`.day-ends input[data-end="${end}"]`)).value;
    click(picker, '[data-direction="next"]');
    expect(box("first")).toBe("31 December 2021");
    expect(picker.querySelector(".day-ends .active input")?.getAttribute("data-end")).toBe("first");
    expect(picker.querySelector('[data-direction="next"]')?.hasAttribute("disabled")).toBe(true);
    // The second day takes the change when it has the frame, counted from its own day.
    /** @type {HTMLInputElement} */ (picker.querySelector('.day-ends input[data-end="second"]')).focus();
    click(picker, '[data-direction="next"]');
    expect(box("second")).toBe("31 December 2021");
    click(picker, '[data-direction="previous"]');
    expect(box("second")).toBe("11 March 2021");
    expect(box("first")).toBe("31 December 2021");
    expect(sent).not.toHaveBeenCalled();
  });

  test("does not ask for two days while one is not given", () => {
    const { picker, sent } = control();
    click(picker, ".day-open");
    click(picker, '[data-mode="two"]');
    click(picker, "[data-show]");
    expect(sent).not.toHaveBeenCalled();
    expect(picker.querySelector(".day-error")?.textContent).toBe("Give two days.");
  });

  test("starts with two days on a page with two days, and goes back to one day", () => {
    const { picker, form, sent } = control({ from: "2020-07-02" });
    const field = /** @type {HTMLInputElement} */ (picker.querySelector(".day-text"));
    expect(field.value).toBe("2 July 2020 – 15 March 2021");
    expect(field.readOnly).toBe(true);
    click(picker, ".day-open");
    expect(picker.querySelector('[data-mode="two"]')?.getAttribute("aria-pressed")).toBe("true");
    click(picker, '[data-mode="one"]');
    expect(field.value).toBe("15 March 2021");
    click(picker, '[data-day="2021-03-15"]');
    expect(sent).toHaveBeenCalledOnce();
    // One day goes to the address for one day, without the provision.
    expect(values(form)).toEqual({ on: "2021-03-15" });
    expect(form.action).toContain("/history");
  });

  test("has no switch on a page that takes one day only", () => {
    const { picker } = control({ range: false });
    click(picker, ".day-open");
    expect(picker.querySelector(".day-mode")).toBeNull();
  });

  test("closes on Escape and gives the focus back to its button", () => {
    const { picker } = control();
    click(picker, ".day-open");
    picker
      .querySelector(".day-grid .chosen")
      ?.dispatchEvent(new KeyboardEvent("keydown", { key: "Escape", bubbles: true }));
    expect(picker.querySelector(".day-panel")).toBeNull();
    expect(document.activeElement).toBe(picker.querySelector(".day-open"));
  });

  test("moves the focus by a day and a week with the arrow keys", () => {
    const { picker } = control();
    click(picker, ".day-open");
    picker
      .querySelector(".day-grid .chosen")
      ?.dispatchEvent(new KeyboardEvent("keydown", { key: "ArrowDown", bubbles: true }));
    expect(picker.querySelector('.day-grid [tabindex="0"]')?.getAttribute("data-day")).toBe("2021-03-22");
  });
});
