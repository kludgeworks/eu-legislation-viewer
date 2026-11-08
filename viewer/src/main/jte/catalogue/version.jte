@import dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.Version
@import dev.groknull.kludgeworks.eu.legislation.viewer.chrome.Chrome
@import dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.web.view.ArrivingView
@import dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.web.view.MastheadView
@import dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.web.view.ReadingView
@import dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.web.view.TimelineView
@param Chrome chrome
@param Version version
@param MastheadView masthead
@param TimelineView timeline
@param ReadingView reading
@param gg.jte.Content text
@param String receipt = null
@param ArrivingView arriving = null
<%-- Shows an act's page in Read mode. It has the act's head, its timeline, the Reading card and
     the text. htmx boosts its links and the date form. Each one loads the page it links to, swaps
     in that page's body and pushes its address. Thus a move between versions does not reload the
     page. Only hx-boost is inherited. The text loads with htmx attributes of its own.
     When the reader comes from the preparing page (receipt), a line says what was prepared. The
     content below that line fades in. If versions appeared after the act was prepared and are being
     prepared now, a notice says so (arriving). --%>
@template.layout.page(chrome = chrome, title = version.celex(), content = @`
  <div id="page" class="${receipt != null ? "arrived" : null}" hx-boost:inherited="true">
    @template.catalogue.masthead(masthead = masthead)
    @template.catalogue.timeline(timeline = timeline)
    @if(receipt != null)
      <action-receipt class="receipt" role="status">✓ ${receipt} · <button type="button" class="dismiss">dismiss</button></action-receipt>
    @endif
    @if(arriving != null)
      @template.catalogue.arriving(arriving = arriving)
    @endif
    @template.catalogue.reading(reading = reading)
    ${text}
  </div>
`)
