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
     the text --%>
@template.layout.page(chrome = chrome, title = version.celex(), content = @`
  @template.catalogue.masthead(masthead = masthead)
  @template.catalogue.timeline(timeline = timeline)
  @template.catalogue.reading(reading = reading)
  ${text}
`)
