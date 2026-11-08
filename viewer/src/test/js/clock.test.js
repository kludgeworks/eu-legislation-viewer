// SPDX-License-Identifier: MIT
// @ts-check
import { afterEach, beforeAll, describe, expect, test, vi } from "vitest";

/** @type {typeof import("../../main/resources/static/js/clock.js")} */
let clock;

beforeAll(async () => {
  // fake timers before the import, so any intervals never run uncontrolled
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

describe("ElapsedTime", () => {
  test("counts up from the server's seconds, from when the element appeared", () => {
    vi.setSystemTime(10_000);
    document.body.innerHTML = `<elapsed-time seconds="60"><time datetime="PT60S">1:00</time></elapsed-time>`;

    expect(one("time").textContent).toBe("1:00");
    expect(one("time").getAttribute("datetime")).toBe("PT60S");

    vi.advanceTimersByTime(5_400);
    expect(one("time").textContent).toBe("1:05");
    expect(one("time").getAttribute("datetime")).toBe("PT65S");
  });

  test("stops counting when disconnected", () => {
    vi.setSystemTime(10_000);
    document.body.innerHTML = `<elapsed-time seconds="60"><time datetime="PT60S">1:00</time></elapsed-time>`;
    const element = one("elapsed-time");

    element.remove();
    vi.advanceTimersByTime(5_000);

    expect(element.querySelector("time")?.textContent).toBe("1:00");
    expect(element.querySelector("time")?.getAttribute("datetime")).toBe("PT60S");
  });

  test("does not restart clock when moved in the DOM", () => {
    vi.setSystemTime(10_000);
    document.body.innerHTML = `
      <div id="first"><elapsed-time seconds="60"><time datetime="PT60S">1:00</time></elapsed-time></div>
      <div id="second"></div>`;
    const element = one("elapsed-time");

    vi.advanceTimersByTime(5_000);
    expect(one("time").textContent).toBe("1:05");
    expect(one("time").getAttribute("datetime")).toBe("PT65S");

    one("#second").append(element);
    vi.advanceTimersByTime(2_000);

    expect(one("time").textContent).toBe("1:07");
    expect(one("time").getAttribute("datetime")).toBe("PT67S");
  });

  test("updates when seconds attribute changes", () => {
    vi.setSystemTime(10_000);
    document.body.innerHTML = `<elapsed-time seconds="60"><time datetime="PT60S">1:00</time></elapsed-time>`;
    const element = /** @type {import("../../main/resources/static/js/clock.js").ElapsedTime} */ (
      one("elapsed-time")
    );

    element.seconds = 120;
    expect(one("time").textContent).toBe("2:00");
    expect(one("time").getAttribute("datetime")).toBe("PT120S");

    vi.advanceTimersByTime(3_000);
    expect(one("time").textContent).toBe("2:03");
    expect(one("time").getAttribute("datetime")).toBe("PT123S");
  });
});

describe("RotatingAside", () => {
  const asides = `<rotating-aside class="aside" asides="First…\nSecond…">First…</rotating-aside>`;

  test("changes the aside at once if the reader asks for reduced motion", () => {
    document.body.innerHTML = asides;
    const aside = /** @type {import("../../main/resources/static/js/clock.js").RotatingAside} */ (
      one(".aside")
    );

    aside.turn(true, () => 0);

    expect(aside.textContent).toBe("Second…");
    expect(aside.classList.contains("turning")).toBe(false);
  });

  test("fades the aside out, then changes it and fades it in", () => {
    document.body.innerHTML = asides;
    const aside = /** @type {import("../../main/resources/static/js/clock.js").RotatingAside} */ (
      one(".aside")
    );

    aside.turn(false, () => 0);
    expect(aside.classList.contains("turning")).toBe(true);
    expect(aside.textContent).toBe("First…");

    vi.advanceTimersByTime(clock.FADE);
    expect(aside.classList.contains("turning")).toBe(false);
    expect(aside.textContent).toBe("Second…");
  });

  test("rotates automatically on a timer", () => {
    document.body.innerHTML = asides;
    const aside = one(".aside");

    vi.advanceTimersByTime(4_000);
    expect(aside.classList.contains("turning")).toBe(true);

    vi.advanceTimersByTime(clock.FADE);
    expect(aside.classList.contains("turning")).toBe(false);
    expect(aside.textContent).toBe("Second…");
  });

  test("stops rotating when disconnected", () => {
    document.body.innerHTML = asides;
    const aside = one(".aside");

    aside.remove();
    vi.advanceTimersByTime(4_000 + clock.FADE);

    expect(aside.textContent).toBe("First…");
  });
});
