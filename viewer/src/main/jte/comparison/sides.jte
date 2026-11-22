@import dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.ComparePage
@import dev.groknull.kludgeworks.eu.legislation.viewer.comparison.web.view.DifferencesView
@import dev.groknull.kludgeworks.eu.legislation.viewer.legaltext.LegalRenderer
@import java.util.List
@param List<DifferencesView.Shown> shown
@param LegalRenderer html
@param ComparePage page
@param String from
@param String to
<%-- Shows the wording of one provision on the two days of a comparison, side by side. The Compare
     page of one provision loads it. The column heads name the two days (from, to). Each difference
     at the provision is a row, as in the Synopsis: words that it lost are struck through under the
     earlier day, and words that it gained are underlined under the later day. Where a text does not
     have the provision, the cell says so. An annex inserted whole is a box that shows its size
     (annexfold.jte). If the provision is the same in both texts, a line says so. --%>
@if(shown.isEmpty())
  <p class="hint">The wording of this provision is the same on the two days.</p>
@else
  <div class="ledger sides legal-text legal-text-consolidated" role="table" aria-label="The wording on the two days">
    <div class="ledger-head" role="row"><div role="columnheader">On<span class="day">${from}</span></div><div role="columnheader">On<span class="day">${to}</span></div></div>
    @for(var each : shown)
      !{var occurrence = html.element(each.folded(page));}
      <div class="ledger-row difference-${each.kind()}" role="row">
        <div class="from" role="cell">@if(each.kind().equals("inserted"))<p class="absent">Not in the text on this day</p>@else${occurrence}@endif</div>
        <div class="to" role="cell">@if(each.kind().equals("deleted"))<p class="absent">Not in the text on this day</p>@elseif(each.annexInserted())@template.comparison.annexfold(shown = each, page = page)@else${occurrence}@endif</div>
      </div>
    @endfor
  </div>
@endif
