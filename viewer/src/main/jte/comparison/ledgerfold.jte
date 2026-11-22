@import dev.groknull.kludgeworks.eu.legislation.viewer.comparison.web.view.DifferencesView
@param DifferencesView.Fold fold
@param String same
<%-- Shows a fold in the Synopsis view. A fold of occurrences that are the same in both texts opens in
     place, as a fold in the Differences view does. Each occurrence then shows as a row. htmx loads the occurrences
     from the same address, followed by the fold's eIds. Occurrences that an act filter leaves out are
     different in the two texts, so they stay folded. --%>
@if(fold.same() && !same.isEmpty())
  <details class="ledger-fold" role="row" hx-get="${same}&${fold.query()}" hx-trigger="toggle once" hx-target="find .ledger-fold-rows" hx-swap="innerHTML">
    <summary role="cell">${fold.label()} — ${fold.reason()}<span class="show"> · <u>show</u></span></summary>
    <div class="ledger-fold-rows"><p class="status">Loading…</p></div>
  </details>
@else
  <div class="ledger-fold" role="row"><span role="cell">${fold.label()} — ${fold.reason()}</span></div>
@endif
