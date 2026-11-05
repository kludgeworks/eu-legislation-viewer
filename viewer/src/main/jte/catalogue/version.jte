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
<%-- An act's page in Read mode (Design: The act page): its head, its timeline, the Reading card,
     the text. Its links and the date form are boosted (htmx): each loads the page it links to and
     swaps in its body, pushing its address, so moving between versions doesn't reload the page. Only
     hx-boost is inherited: the text's part polls and loads with attributes of its own --%>
@template.layout.page(chrome = chrome, title = version.celex(), content = @`
  <div id="page" hx-boost:inherited="true">
    @template.catalogue.masthead(masthead = masthead)
    @template.catalogue.timeline(timeline = timeline)
    @template.catalogue.reading(reading = reading)
    ${text}
  </div>
`)
