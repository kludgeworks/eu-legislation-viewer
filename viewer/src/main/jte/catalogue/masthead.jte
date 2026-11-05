@import dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.web.view.MastheadView
@param MastheadView masthead
<%-- The act page's head (MastheadView): the act's title, its ELI and CELEX on EUR-Lex, the
     language switch (details, its links the same page in each language the version is in), and the
     mode switch, the current mode marked, the other a link --%>
<header class="mast">
  <div>
    <h1 lang="${masthead.titleLanguage()}">${masthead.title()}</h1>
    <div class="ids">
      <a class="mono" href="${masthead.eliHref()}">${masthead.eli()}</a>
      <a class="mono" href="${masthead.celexHref()}">${masthead.celex()}</a>
      <details class="lang">
        <summary aria-label="Language: ${masthead.language()}. Change language">${masthead.language()} ▾</summary>
        <div class="menu">
          <p>This version is in these languages. Switching keeps the date.</p>
          <ul>
            @for(var choice : masthead.languages())
              <li><a href="${choice.href()}" lang="${choice.iso()}" hreflang="${choice.iso()}" aria-current="${choice.current() ? "page" : null}">${choice.name()}</a></li>
            @endfor
          </ul>
          @if(!masthead.missing().isEmpty())
            <p class="missing">Not for this version: ${String.join(", ", masthead.missing())}</p>
          @endif
        </div>
      </details>
    </div>
  </div>
  <nav class="mode" aria-label="Mode">
    @if(masthead.read() != null)<a href="${masthead.read()}">Read</a>@else<span aria-current="page">Read</span>@endif
    @if(masthead.compare() != null)<a href="${masthead.compare()}">Compare</a>@else<span aria-current="page">Compare</span>@endif
  </nav>
</header>
