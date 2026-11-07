@import dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.web.view.SidenoteView
@import dev.groknull.kludgeworks.eu.legislation.viewer.comparison.Difference.Sibling
@import dev.groknull.kludgeworks.eu.legislation.viewer.comparison.web.view.ChangesView
@import dev.groknull.kludgeworks.eu.legislation.viewer.content.Element
@import dev.groknull.kludgeworks.eu.legislation.viewer.content.web.LegalRenderer
@param Sibling unit
@param Element element
@param SidenoteView.History history = null
@param LegalRenderer html
<%-- Shows a difference's neighbour in the Synopsis ledger. The neighbour is a unit that is the
     same in both texts. Both columns show it as the later text has it. The last column shows, in
     grey, what last made the unit as it is and when that change applies. --%>
<div class="ledger-row ledger-same" role="row">
  <div class="provision" role="cell">${ChangesView.cited(unit)}</div>
  <div class="from" role="cell">${html.element(element)}</div>
  <div class="to" role="cell">${html.element(element)}</div>
  <div class="history" role="cell">@if(history != null)${history.said()}@if(history.applies() != null) · @template.catalogue.applies(applies = history.applies())@endif@endif</div>
</div>
