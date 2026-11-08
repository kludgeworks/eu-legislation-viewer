@import dev.groknull.kludgeworks.eu.legislation.viewer.comparison.web.view.ChangesView
@param ChangesView.Shown shown
@param String part
<%-- Shows an annex inserted whole as a fold that says its size. The fold loads the annex from the
     address of its difference (part) when the reader opens it, so the annex is not sent until
     then: one annex can be tens of MB. --%>
<details class="annex-inserted" hx-get="${part}" hx-trigger="toggle once" hx-target="find .fold-text" hx-swap="innerHTML"><summary>Inserted whole · ${shown.size()} · <span class="show">show</span></summary><div class="fold-text"><p class="status">Loading…</p></div></details>
