@import dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.web.chrome.Chrome
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
@param String receipt = null
@param ArrivingView arriving = null
<%-- An act's page in Compare mode (Design: Mode: Compare): its head, its timeline with the range
     compared, the From and To cards, then the changes. Boosted as the Read page is: only hx-boost
     is inherited, the changes load with attributes of their own. Arriving from the preparing page
     (receipt), a line says what was prepared above what fades in; versions that appeared since,
     being prepared, a notice (arriving) --%>
@template.layout.page(chrome = chrome, title = title, content = @`
  <div id="page" class="${receipt != null ? "arrived" : null}" hx-boost:inherited="true">
    @template.catalogue.masthead(masthead = masthead)
    @template.catalogue.timeline(timeline = timeline)
    @if(receipt != null)
      <p class="receipt" role="status">✓ ${receipt} · <button type="button" hx-on:click="this.parentElement.remove()">dismiss</button></p>
    @endif
    @if(arriving != null)
      @template.catalogue.arriving(arriving = arriving)
    @endif
    @template.catalogue.cards(compare = compare)
    @if(compare.note() != null)<p class="compare-note" role="status">${compare.note()}</p>@endif
    ${changes}
  </div>
`)
