@import dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.Act
@import dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.Version
@import dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.web.chrome.Chrome
@import dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.web.view.TimelineView
@import dev.groknull.kludgeworks.eu.legislation.viewer.language.Language
@param Chrome chrome
@param Act act
@param Version version
@param Language language
@param String eurLex
@param TimelineView timeline
@param gg.jte.Content text
@template.layout.page(chrome = chrome, title = version.celex(), content = @`
  <header class="version">
    <p class="version-celex">${version.celex()}</p>
    <p class="version-kind">
      @if(version.kind() == Version.Kind.ORIGINAL)
        The original text, applying from <time datetime="${version.date().toString()}">${version.date().toString()}</time>
      @else
        Consolidated text, applying from <time datetime="${version.date().toString()}">${version.date().toString()}</time>
      @endif
    </p>
    <p class="version-links"><a href="${eurLex}">On EUR-Lex</a></p>
  </header>
  @template.catalogue.timeline(timeline = timeline)
  ${text}
`)
