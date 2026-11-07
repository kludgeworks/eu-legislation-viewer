// SPDX-License-Identifier: MIT
// The amendment marks of a version's text in Read (legal/provenance.jte, Design D-20): each mark's
// hover says what happened and by which act, by its number (Replaced by 2017/815); a click, or
// Enter, opens that act's change in Compare: the comparisons of the act the text's container
// names (data-compare), limited to the act's changes.

// 32017R0815 → 2017/815; 32019R0103R(01) → corrigendum 1 to 2019/103
const number = (celex) => {
  const act = /^\d(\d{4})[A-Z](\d+)(?:R\((\d+)\))?$/.exec(celex || "");
  if (!act) {
    return celex;
  }
  const name = `${act[1]}/${Number(act[2])}`;
  return act[3] ? `corrigendum ${Number(act[3])} to ${name}` : name;
};

const said = (mark) => {
  const action = mark.dataset.action || "amended";
  return `${action.charAt(0).toUpperCase()}${action.slice(1)} by ${number(mark.dataset.act)} · open this change in Compare`;
};

const open = (mark) => {
  const text = mark.closest("[data-compare]");
  if (text && mark.dataset.act) {
    location.href = `${text.dataset.compare}?act=${encodeURIComponent(mark.dataset.act)}`;
  }
};

document.addEventListener("htmx:afterSettle", () => {
  for (const mark of document.querySelectorAll("[data-compare] .provenance[data-act]")) {
    mark.title = said(mark);
  }
});
document.addEventListener("click", (event) => {
  const mark = event.target.closest("[data-compare] .provenance[data-act]");
  if (mark) {
    open(mark);
  }
});
document.addEventListener("keydown", (event) => {
  const mark = event.target.closest && event.target.closest("[data-compare] .provenance[data-act]");
  if (mark && event.key === "Enter") {
    open(mark);
  }
});
