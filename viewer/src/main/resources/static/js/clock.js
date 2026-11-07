// SPDX-License-Identifier: MIT
// The preparing page's clock (catalogue/preparing/progress.jte): each time with data-elapsed, the
// seconds the preparation had taken when the server rendered it, counts on every second from when
// it appeared, m:ss or h:mm:ss, until the stream swaps in the next. Counted from the server's
// figure, not the browser's clock, so a clock set wrong doesn't show.
const seen = new WeakMap();
const pad = (n) => String(n).padStart(2, "0");
setInterval(() => {
  for (const time of document.querySelectorAll("time[data-elapsed]")) {
    if (!seen.has(time)) {
      seen.set(time, Date.now());
    }
    const seconds =
      Number(time.dataset.elapsed) + Math.floor((Date.now() - seen.get(time)) / 1000);
    const h = Math.floor(seconds / 3600);
    const m = Math.floor((seconds % 3600) / 60);
    const s = seconds % 60;
    time.textContent = h > 0 ? `${h}:${pad(m)}:${pad(s)}` : `${m}:${pad(s)}`;
  }
}, 1000);

// While it waits its turn, the light aside beside the plain line (Design D-59): another of its
// lines, at random, every four seconds; faded out and in, unless the reader asks for less motion
// (the stylesheet drops the fade then, and this waits for nothing).
const still = window.matchMedia("(prefers-reduced-motion: reduce)");
setInterval(() => {
  for (const aside of document.querySelectorAll("[data-asides]")) {
    const lines = aside.dataset.asides.split("\n").filter((line) => line !== aside.textContent);
    const next = lines[Math.floor(Math.random() * lines.length)];
    if (still.matches) {
      aside.textContent = next;
      continue;
    }
    aside.classList.add("turning");
    setTimeout(() => {
      aside.textContent = next;
      aside.classList.remove("turning");
    }, 400);
  }
}, 4000);
