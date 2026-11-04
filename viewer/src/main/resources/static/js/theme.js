// SPDX-License-Identifier: MIT
// The theme switch, in place: the reader's choice set on the page at once, then posted by fetch,
// for the server to keep (POST /theme, 204), so the page isn't reloaded. Without JavaScript the
// form posts and the server sends the reader back to the page.
document.addEventListener("submit", (event) => {
  const form = event.target;
  if (!form.matches("form.theme") || !event.submitter) {
    return;
  }
  event.preventDefault();
  document.documentElement.dataset.theme = event.submitter.value;
  for (const button of form.querySelectorAll("button")) {
    button.setAttribute("aria-pressed", String(button === event.submitter));
  }
  fetch(form.action, {
    method: "POST",
    body: new URLSearchParams(new FormData(form, event.submitter)),
  });
});
