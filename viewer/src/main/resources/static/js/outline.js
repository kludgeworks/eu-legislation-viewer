// SPDX-License-Identifier: MIT
// A comparison's outline (catalogue/outlinehead.jte), loaded with its view by htmx: its head says
// which difference the reader is at as they scroll, "Difference 663 of 1,828 · Annex IV", with ↑ ↓
// to the one before and after (Design D-44, D-25); its button collapses it to a rail of the parts
// and their counts (D-33), which the reader's browser remembers.
const RAIL = "outline-rail";
const AT = 140;
const format = (n) => n.toLocaleString("en");

const remembered = () => {
  try {
    return localStorage.getItem(RAIL) === "true";
  } catch {
    return false;
  }
};

const rail = (nav, on) => {
  nav.classList.toggle("rail", on);
  const button = nav.querySelector(".outline-rail");
  if (button) {
    button.setAttribute("aria-pressed", String(on));
    const label = on ? "Expand the outline" : "Collapse the outline";
    button.setAttribute("aria-label", label);
    button.title = label;
    button.textContent = on ? "⇥" : "⇤";
  }
};

// the part a difference is in: the last of the outline's top entries that starts at or before it
const part = (nav, number) => {
  let found = "";
  for (const entry of nav.querySelectorAll(":scope > ul > li")) {
    const link = entry.querySelector("a.has");
    const first = link && Number(link.getAttribute("href").replace("#difference-", ""));
    if (first && first <= number) {
      found = link.querySelector("span").textContent.trim();
    }
  }
  return found;
};

const follow = () => {
  const nav = document.querySelector("nav.outline.counted");
  const line = nav && nav.querySelector(".outline-follow");
  if (!line) {
    return;
  }
  const total = Number(line.dataset.total);
  let number = 0;
  for (const difference of document.querySelectorAll("[id^='difference-']")) {
    if (difference.getBoundingClientRect().top > AT) {
      break;
    }
    number = Number(difference.id.replace("difference-", ""));
  }
  const where = line.querySelector(".where");
  const up = line.querySelector(".up");
  const down = line.querySelector(".down");
  if (number === 0) {
    where.textContent = total === 1 ? "1 difference" : `${format(total)} differences`;
    up.setAttribute("aria-disabled", "true");
    down.href = "#difference-1";
    down.removeAttribute("aria-disabled");
    return;
  }
  const inPart = part(nav, number);
  where.textContent =
    `Difference ${format(number)} of ${format(total)}` + (inPart ? ` · ${inPart}` : "");
  up.href = `#difference-${Math.max(number - 1, 1)}`;
  up.toggleAttribute("aria-disabled", number <= 1);
  down.href = `#difference-${Math.min(number + 1, total)}`;
  down.toggleAttribute("aria-disabled", number >= total);
};

let pending = false;
const soon = () => {
  if (!pending) {
    pending = true;
    requestAnimationFrame(() => {
      pending = false;
      follow();
    });
  }
};

document.addEventListener("scroll", soon, { passive: true });
document.addEventListener("click", (event) => {
  const button = event.target.closest(".outline-rail");
  if (button) {
    const nav = button.closest("nav.outline");
    const on = !nav.classList.contains("rail");
    rail(nav, on);
    try {
      localStorage.setItem(RAIL, String(on));
    } catch {
      // kept for this page only
    }
  }
});
document.addEventListener("htmx:afterSettle", () => {
  for (const nav of document.querySelectorAll("nav.outline.counted")) {
    rail(nav, remembered());
  }
  follow();
});
