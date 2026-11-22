@import dev.groknull.kludgeworks.eu.legislation.viewer.comparison.web.view.SidenoteView
@import dev.groknull.kludgeworks.eu.legislation.viewer.comparison.Difference.Sibling
@import dev.groknull.kludgeworks.eu.legislation.viewer.comparison.web.view.DifferencesView
@import dev.groknull.kludgeworks.eu.legislation.viewer.content.Element
@import dev.groknull.kludgeworks.eu.legislation.viewer.legaltext.LegalRenderer
@param Sibling occurrence
@param Element element
@param SidenoteView.History history = null
@param LegalRenderer html
<%-- Shows a difference's neighbour in the Synopsis ledger. The neighbour is an occurrence that is the
     same in both texts. Both columns show it as the later text has it. The last column shows, in
     grey, what last made the occurrence as it is and when that change applies. --%>
<div class="ledger-row ledger-same" role="row">
  <div class="provision" role="cell">${DifferencesView.cited(occurrence)}</div>
  <div class="from" role="cell">${html.element(element)}</div>
  <div class="to" role="cell">${html.element(element)}</div>
  <div class="history" role="cell">@if(history != null)${history.said()}@if(history.applies() != null) · @template.comparison.applies(applies = history.applies())@endif@endif</div>
</div>
