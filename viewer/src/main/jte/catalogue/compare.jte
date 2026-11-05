@import dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.web.chrome.Chrome
@import dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.web.view.CompareView
@import dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.web.view.MastheadView
@import dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.web.view.TimelineView
@param Chrome chrome
@param String title
@param MastheadView masthead
@param TimelineView timeline
@param CompareView compare
@param gg.jte.Content changes
<%-- An act's page in Compare mode (Design: Mode: Compare): its head, its timeline with the range
     compared, the From and To cards, then the changes. Boosted as the Read page is: only hx-boost
     is inherited, the changes' part polls with attributes of its own --%>
@template.layout.page(chrome = chrome, title = title, content = @`
  <div id="page" hx-boost:inherited="true">
    @template.catalogue.masthead(masthead = masthead)
    @template.catalogue.timeline(timeline = timeline)
    @template.catalogue.cards(compare = compare)
    ${changes}
  </div>
`)
