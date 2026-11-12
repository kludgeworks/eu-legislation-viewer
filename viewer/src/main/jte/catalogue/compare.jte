@import dev.groknull.kludgeworks.eu.legislation.viewer.chrome.Chrome
@import dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.web.view.ArrivingView
@import dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.web.view.CompareView
@import dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.web.view.MastheadView
@import dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.web.view.TimelineView
@param Chrome chrome
@param String title
@param MastheadView masthead
@param TimelineView timeline
@param CompareView compare
@param gg.jte.Content changes
@param boolean arrived = false
@param ArrivingView arriving = null
@param String view = null
<%-- Shows an act's page in Compare mode. It has the act's head, its timeline with the compared
     range, the From and To cards, and then the changes. The chips of the acts between the cards and
     the changes are the comparison module's: each has a slot that loads them from that module.
     Its links are boosted, as on the Read page: each swaps in the #page of the page it links to.
     Only hx-boost is inherited, with its target, selection and swap. The slots load with htmx
     attributes of their own. The page's view (view) is the comparison module's code, as the reader
     gave it: while the next page loads, the page shows the shape of the view that is coming.
     When the reader comes from the preparing page (arrived), the cards and the text fade in. If versions appeared after the act was prepared and are being
     prepared now, a notice says so (arriving). --%>
@template.layout.page(chrome = chrome, title = title, content = @`
  <div id="page" class="${arrived ? "arrived" : null}" data-view="${view != null ? view : "changes"}" hx-boost:inherited="target:#page select:#page swap:outerHTML">
    @template.catalogue.skeletons()
    @template.catalogue.masthead(masthead = masthead)
    @template.catalogue.timeline(timeline = timeline)
    @if(arriving != null)
      @template.catalogue.arriving(arriving = arriving)
    @endif
    @template.catalogue.compare.cards(compare = compare)
    @if(compare.note() != null)<p class="compare-note" role="status">${compare.note()}</p>@endif
    ${changes}
  </div>
`)
