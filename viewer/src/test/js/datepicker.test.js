// SPDX-License-Identifier: MIT
// @ts-check
import { afterEach, describe, expect, test, vi } from "vitest";
import { addMonths, around, month, plus, versionOf } from "../../main/resources/static/js/datepicker.js";

const STARTS = ["2015-11-15", "2016-02-01", "2016-02-29", "2017-06-07"];

afterEach(() => {
  document.body.innerHTML = "";
});

describe("the days", () => {
  test("add up across months and years", () => {
    expect(plus("2016-02-28", 1)).toBe("2016-02-29");
    expect(plus("2016-12-31", 1)).toBe("2017-01-01");
    expect(plus("2016-03-01", -1)).toBe("2016-02-29");
    expect(addMonths("2016-01-31", 1)).toBe("2016-02-01");
    expect(addMonths("2016-01-15", -1)).toBe("2015-12-01");
  });

  test("fall in the version that started last on or before them", () => {
    expect(versionOf(STARTS, "2015-11-14")).toBeNull();
    expect(versionOf(STARTS, "2015-11-15")).toBe("2015-11-15");
    expect(versionOf(STARTS, "2016-02-28")).toBe("2016-02-01");
    expect(versionOf(STARTS, "2030-01-01")).toBe("2017-06-07");
  });

  test("have a change before and after, except at the ends", () => {
    expect(around(STARTS, "2016-02-01")).toEqual({ previous: "2015-11-15", next: "2016-02-29" });
    expect(around(STARTS, "2016-02-10")).toEqual({ previous: "2016-02-01", next: "2016-02-29" });
    expect(around(STARTS, "2015-01-01")).toEqual({ previous: null, next: "2015-11-15" });
    expect(around(STARTS, "2017-06-07")).toEqual({ previous: "2016-02-29", next: null });
  });
});

describe("a month", () => {
  test("starts its weeks on Monday", () => {
    // 1 February 2016 was a Monday, 1 November 2015 a Sunday.
    expect(month("2016-02-10", STARTS, "2016-02-10").lead).toBe(0);
    expect(month("2015-11-20", STARTS, "2016-02-10").lead).toBe(6);
  });

  test("rings the starts and shades the version of the chosen day", () => {
    const { cells } = month("2016-02-10", STARTS, "2016-02-10");

    expect(cells).toHaveLength(29);
    expect(cells.filter((cell) => cell.start).map((cell) => cell.number)).toEqual([1, 29]);
    expect(cells.filter((cell) => cell.shaded)).toHaveLength(28);
    expect(cells.filter((cell) => cell.chosen).map((cell) => cell.day)).toEqual(["2016-02-10"]);
  });

  test("marks the days before the first version, and shades none of them", () => {
    const { cells } = month("2015-11-01", STARTS, "2015-11-01");

    expect(cells.filter((cell) => cell.before)).toHaveLength(14);
    expect(cells.filter((cell) => cell.shaded)).toHaveLength(0);
  });
});

/**
 * Returns a picker on `day`, connected, with its form and a way to open it.
 *
 * @param {string} day
 */
const picker = async (day) => {
  document.body.innerHTML = `<date-picker data-starts="${STARTS.join(" ")}"><form class="on" action="/act">
    <label for="on">The law on</label><input id="on" name="on" type="date" value="${day}"><button>Go</button>
    </form></date-picker>`;
  const form = /** @type {HTMLFormElement} */ (document.querySelector("form"));
  // The element waits for its form with a MutationObserver, whose callbacks come after a tick.
  await Promise.resolve();
  return { form, open: () => /** @type {HTMLButtonElement} */ (document.querySelector(".date-button")).click() };
};

describe("the picker", () => {
  test("hides the box and Go, and labels its button with the day", async () => {
    await picker("2016-02-10");

    const button = /** @type {HTMLButtonElement} */ (document.querySelector(".date-button"));
    expect(button.textContent).toContain("2016");
    expect(document.querySelector("label")?.getAttribute("for")).toBe(button.id);
    expect(document.querySelector("date-picker")?.classList.contains("ready")).toBe(true);
  });

  test("opens on the chosen day's month, with the focus on that day", async () => {
    (await picker("2016-02-10")).open();

    expect(document.querySelectorAll(".calendar-days .start")).toHaveLength(2);
    expect(document.activeElement?.getAttribute("data-day")).toBe("2016-02-10");
    expect(document.querySelector(".date-button")?.getAttribute("aria-expanded")).toBe("true");
  });

  test("sends the form with the day picked", async () => {
    const { form, open } = await picker("2016-02-10");
    const sent = vi.fn((/** @type {SubmitEvent} */ event) => event.preventDefault());
    form.addEventListener("submit", sent);
    open();

    /** @type {HTMLButtonElement} */ (document.querySelector('[data-day="2016-02-20"]')).click();

    expect(sent).toHaveBeenCalledOnce();
    expect(new FormData(form).get("on")).toBe("2016-02-20");
    expect(document.querySelector(".calendar")).toBeNull();
  });

  test("steps to the next change, and greys the step with nowhere to go", async () => {
    const { form, open } = await picker("2017-06-07");
    form.addEventListener("submit", (event) => event.preventDefault());
    open();

    const [previous, next] = /** @type {HTMLButtonElement[]} */ ([...document.querySelectorAll(".calendar-steps button")]);
    expect(next?.disabled).toBe(true);
    previous?.click();
    expect(new FormData(form).get("on")).toBe("2016-02-29");
  });

  test("moves by a week with the arrow keys, into the next month", async () => {
    (await picker("2016-02-25")).open();

    document.activeElement?.dispatchEvent(new KeyboardEvent("keydown", { key: "ArrowDown", bubbles: true }));

    expect(document.activeElement?.getAttribute("data-day")).toBe("2016-03-03");
  });

  test("closes on Escape and gives the focus back", async () => {
    (await picker("2016-02-10")).open();

    document.activeElement?.dispatchEvent(new KeyboardEvent("keydown", { key: "Escape", bubbles: true }));

    expect(document.querySelector(".calendar")).toBeNull();
    expect(document.activeElement?.classList.contains("date-button")).toBe(true);
  });
});
