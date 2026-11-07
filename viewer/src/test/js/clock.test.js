// SPDX-License-Identifier: MIT
// @ts-check
import { afterEach, beforeAll, describe, expect, test, vi } from "vitest";

/** @type {typeof import("../../main/resources/static/js/clock.js")} */
let clock;

beforeAll(async () => {
  // fake timers before the import, so the script's intervals never run on their own
  vi.useFakeTimers();
  clock = await import("../../main/resources/static/js/clock.js");
});

afterEach(() => {
  document.body.innerHTML = "";
});

/**
 * Returns the one element of the page that matches a selector.
 *
 * @param {string} selector
 * @returns {HTMLElement}
 */
const one = (selector) => /** @type {HTMLElement} */ (document.querySelector(selector));

describe("elapsed", () => {
  test("formats less than an hour as m:ss", () => {
    expect(clock.elapsed(0)).toBe("0:00");
    expect(clock.elapsed(252)).toBe("4:12");
  });

  test("formats an hour or more as h:mm:ss", () => {
    expect(clock.elapsed(3600)).toBe("1:00:00");
    expect(clock.elapsed(3852)).toBe("1:04:12");
  });
});

describe("tick", () => {
  test("counts up from the server's seconds, from when the clock appeared", () => {
    document.body.innerHTML = `<time data-elapsed="60">1:00</time>`;

    clock.tick(document, 10_000);
    expect(one("time").textContent).toBe("1:00");

    clock.tick(document, 15_400);
    expect(one("time").textContent).toBe("1:05");
  });
});

describe("next", () => {
  const lines = ["Taking a number…", "Pending entry into force…", "Without prejudice…"];

  test("selects another aside than the one shown", () => {
    expect(clock.next(lines, lines[0] ?? "", () => 0)).toBe(lines[1]);
    expect(clock.next(lines, lines[0] ?? "", () => 0.99)).toBe(lines[2]);
  });

  test("keeps the aside shown if there is no other", () => {
    expect(clock.next(["Only one…"], "Only one…", () => 0.5)).toBe("Only one…");
  });
});

describe("turn", () => {
  const asides = `<p class="aside" data-asides="First…\nSecond…">First…</p>`;

  test("changes the aside at once if the reader asks for reduced motion", () => {
    document.body.innerHTML = asides;

    clock.turn(document, true, () => 0);

    expect(one(".aside").textContent).toBe("Second…");
    expect(one(".aside").classList.contains("turning")).toBe(false);
  });

  test("fades the aside out, then changes it and fades it in", () => {
    document.body.innerHTML = asides;

    clock.turn(document, false, () => 0);
    expect(one(".aside").classList.contains("turning")).toBe(true);
    expect(one(".aside").textContent).toBe("First…");

    vi.advanceTimersByTime(clock.FADE);
    expect(one(".aside").classList.contains("turning")).toBe(false);
    expect(one(".aside").textContent).toBe("Second…");
  });
});
