@import dev.groknull.kludgeworks.eu.legislation.viewer.chrome.Chrome
@import dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.web.view.ArrivingView
@import dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.web.view.MastheadView
@import dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.web.view.ReadingView
@import dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.web.view.TimelineView
@param Chrome chrome
@param MastheadView masthead
@param TimelineView timeline
@param ReadingView reading
@param gg.jte.Content text
@param boolean arrived = false
@param ArrivingView arriving = null
<%-- Shows an act's page in Read mode. It has the act's head, its timeline, the Reading card and
     the text. htmx boosts its links and the date form. Each one loads the page it links to, swaps
     in that page's #page for this one and pushes its address. Thus a move between days does not
     reload the page. Only hx-boost is inherited, with its target, selection and swap. The text loads
     with htmx attributes of its own. While the next page loads, the page shows its shape
     (skeletons, js/loading.js).
     When the reader comes from the preparing page (arrived), the cards and the text fade in. If
     versions appeared after the act was prepared and are being prepared now, a notice says so
     (arriving). --%>
@template.layout.page(chrome = chrome, title = reading.title(), content = @`
  <div id="page" class="${arrived ? "arrived" : null}" data-view="read" hx-boost:inherited="target:#page select:#page swap:outerHTML">
    @template.catalogue.skeletons()
    @template.catalogue.masthead(masthead = masthead)
    @template.catalogue.timeline(timeline = timeline)
    @if(arriving != null)
      @template.catalogue.arriving(arriving = arriving)
    @endif
    @template.catalogue.reading(reading = reading)
    ${text}
  </div>
`)
