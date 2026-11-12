@import dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.web.view.ReadingView
@param ReadingView reading
<%-- Shows the Reading card: the law on the day the reader asked for. ReadingView supplies the
     data. The day is the headline. Under it: which version that is and how long it applied, the
     acts that made it, a line where the act didn't apply yet, and an amber line where the text lags
     the law on the day. Then the version's consolidated CELEX, small, with a copy button.
     Before the act's first version there is no version: a dash stands for it, and the panel in
     place of the text says why. A GET form asks for the law on another day. A link goes to the
     comparison with the version before, or after for the first version. --%>
<section class="selection" aria-label="Reading">
  <article class="card">
    <span class="card-kind">The law on</span>
    <span class="card-day">${reading.day()}</span>
    @if(reading.version() != null)
      <span>${reading.version()}</span>
      <span title="${reading.published()}">${reading.made()}</span>
    @else
      <span>—</span>
    @endif
    @for(var note : reading.notes())
      <span>${note}</span>
    @endfor
    @for(var lag : reading.lags())
      <span class="lag-warning">${lag}</span>
    @endfor
    @if(reading.celex() != null)
      <copy-text class="card-celex" data-text="${reading.celex()}"><a class="mono quiet" href="${reading.eurLex()}" rel="external">${reading.celex()}<span aria-hidden="true"> ↗</span><span class="visually-hidden"> (opens EUR-Lex)</span></a> <button type="button" class="link">copy</button></copy-text>
    @endif
    <form class="on" method="get" action="${reading.action()}">
      <label for="on">The law on</label>
      <input id="on" name="on" type="date" value="${reading.on()}" required>
      <button>Go</button>
    </form>
  </article>
  @if(reading.compare() != null)
    <a class="button" href="${reading.compare()}">${reading.comparing()}</a>
  @endif
</section>
