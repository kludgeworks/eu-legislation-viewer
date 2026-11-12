@import dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.web.view.MastheadView
@param MastheadView masthead
<%-- Shows the head of an act's page. MastheadView supplies the data. The act's title shows in
     English up to its date, and the rest of the title shows under it. Then come the act's ELI and
     CELEX, as quiet links to EUR-Lex (↗).
     The language switch is a details element. It lists the 24 EU languages in the EU's order. Each
     language links to the same page in that language. The page's language is bold. A language that
     a version on the page is not in is greyed, and its hover says why.
     The mode switch marks the current mode and links to the other mode. While the act is being
     prepared, neither mode is a link. --%>
<header class="mast">
  <div>
    <h1 lang="${masthead.titleLanguage()}">${masthead.title()}</h1>
    @if(masthead.subject() != null)<p class="subject" lang="${masthead.titleLanguage()}">${masthead.subject()}</p>@endif
    <div class="ids">
      <a class="mono quiet" href="${masthead.eliHref()}" rel="external" title="On EUR-Lex, by its ELI">${masthead.eli()}<span aria-hidden="true"> ↗</span></a>
      <a class="mono quiet" href="${masthead.celexHref()}" rel="external" title="On EUR-Lex, by its CELEX">${masthead.celex()}<span aria-hidden="true"> ↗</span></a>
      <details class="lang">
        <summary aria-label="Language: ${masthead.language()}. Change language">${masthead.language()} ▾</summary>
        <div class="menu">
          <ul>
            @for(var choice : masthead.languages())
              @if(choice.href() != null)
                <li><a href="${choice.href()}" lang="${choice.iso()}" hreflang="${choice.iso()}" aria-current="${choice.current() ? "page" : null}">${choice.name()}</a></li>
              @else
                <li><span class="missing" lang="${choice.iso()}" title="${choice.missing()}" aria-disabled="true">${choice.name()}</span></li>
              @endif
            @endfor
          </ul>
        </div>
      </details>
    </div>
  </div>
  <nav class="mode" aria-label="Mode">
    @if(masthead.preparing())
      <span aria-current="${masthead.read() == null ? "page" : null}" aria-disabled="true">Read</span>
      <span aria-current="${masthead.compare() == null ? "page" : null}" aria-disabled="true">Compare</span>
    @else
      @if(masthead.read() != null)<a href="${masthead.read()}" data-view="read">Read</a>@else<span aria-current="page">Read</span>@endif
      @if(masthead.compare() != null)<a href="${masthead.compare()}" data-view="changes">Compare</a>@else<span aria-current="page">Compare</span>@endif
    @endif
  </nav>
</header>
