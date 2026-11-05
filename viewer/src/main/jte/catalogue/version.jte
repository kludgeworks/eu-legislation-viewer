@import dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.Version
@import dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.web.chrome.Chrome
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
<%-- An act's page in Read mode (Design: The act page): its head, its timeline, the Reading card,
     the text. Its links and the date form are boosted (htmx): each loads the page it links to and
     swaps in its body, pushing its address, so moving between versions doesn't reload the page. Only
     hx-boost is inherited: the text loads with attributes of its own. Arriving from the preparing
     page (receipt), a line says what was prepared above what fades in --%>
@template.layout.page(chrome = chrome, title = version.celex(), content = @`
  <div id="page" class="${receipt != null ? "arrived" : null}" hx-boost:inherited="true">
    @template.catalogue.masthead(masthead = masthead)
    @template.catalogue.timeline(timeline = timeline)
    @if(receipt != null)
      <p class="receipt" role="status">✓ ${receipt} · <button type="button" hx-on:click="this.parentElement.remove()">dismiss</button></p>
    @endif
    @template.catalogue.reading(reading = reading)
    ${text}
  </div>
`)
