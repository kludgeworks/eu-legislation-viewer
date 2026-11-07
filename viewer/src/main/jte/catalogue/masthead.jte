@import dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.web.view.MastheadView
@param MastheadView masthead
<%-- The act page's head (MastheadView): the act's title, its ELI and CELEX on EUR-Lex, the
     language switch (details: the 24 languages in the EU's order, each a link to the same page in
     it, the page's bold, those a version shown isn't in greyed, why on hover), and the
     mode switch, the current mode marked, the other a link; not yet a link while the act is being
     prepared --%>
<header class="mast">
  <div>
    <h1 lang="${masthead.titleLanguage()}">${masthead.title()}</h1>
    <div class="ids">
      <a class="mono" href="${masthead.eliHref()}">${masthead.eli()}</a>
      <a class="mono" href="${masthead.celexHref()}">${masthead.celex()}</a>
      <details class="lang">
        <summary aria-label="Language: ${masthead.language()}. Change language">${masthead.language()} ▾</summary>
        <div class="menu">
          <p>Switching keeps what you’re looking at. Greyed: not for this version.</p>
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
      @if(masthead.read() != null)<a href="${masthead.read()}">Read</a>@else<span aria-current="page">Read</span>@endif
      @if(masthead.compare() != null)<a href="${masthead.compare()}">Compare</a>@else<span aria-current="page">Compare</span>@endif
    @endif
  </nav>
</header>
