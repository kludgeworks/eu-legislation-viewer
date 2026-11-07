@import dev.groknull.kludgeworks.eu.legislation.viewer.comparison.web.view.ChangesView
@param ChangesView.Fold fold
@param String same
<%-- A Synopsis fold (D-55): units the same in both texts open in place, each a row, as a Changes
     fold does (same: where they load from, the fold's eIds after); units an act filter leaves out
     differ, so they stay folded --%>
@if(fold.same() && !same.isEmpty())
  <details class="ledger-fold" role="row" hx-get="${same}&${fold.query()}" hx-trigger="toggle once" hx-target="find .ledger-fold-rows" hx-swap="innerHTML">
    <summary role="cell">${fold.label()} — ${fold.note()}<span class="show"> · <u>show</u></span></summary>
    <div class="ledger-fold-rows"><p class="status">Loading…</p></div>
  </details>
@else
  <div class="ledger-fold" role="row"><span role="cell">${fold.label()} — ${fold.note()}</span></div>
@endif
