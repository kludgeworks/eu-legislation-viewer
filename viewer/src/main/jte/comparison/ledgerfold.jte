@import dev.groknull.kludgeworks.eu.legislation.viewer.comparison.web.view.ChangesView
@param ChangesView.Fold fold
@param String same
<%-- Shows a fold in the Synopsis view. A fold of units that are the same in both texts opens in
     place, as a fold in the Changes view does. Each unit then shows as a row. htmx loads the units
     from the same address, followed by the fold's eIds. Units that an act filter leaves out are
     different in the two texts, so they stay folded. --%>
@if(fold.same() && !same.isEmpty())
  <details class="ledger-fold" role="row" hx-get="${same}&${fold.query()}" hx-trigger="toggle once" hx-target="find .ledger-fold-rows" hx-swap="innerHTML">
    <summary role="cell">${fold.label()} — ${fold.note()}<span class="show"> · <u>show</u></span></summary>
    <div class="ledger-fold-rows"><p class="status">Loading…</p></div>
  </details>
@else
  <div class="ledger-fold" role="row"><span role="cell">${fold.label()} — ${fold.note()}</span></div>
@endif
