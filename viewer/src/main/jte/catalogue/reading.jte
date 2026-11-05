@import dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.web.view.ReadingView
@param ReadingView reading
<%-- The Reading card (ReadingView): the version, its period and what made it, the date asked, where
     its text lags the law (amber), a GET form for the version on another date, and its comparison
     with the version before --%>
<section class="selection" aria-label="Reading">
  <div class="card">
    <span class="card-kind">Reading</span>
    <a class="card-celex mono" href="${reading.eurLex()}" title="On EUR-Lex">${reading.celex()}</a>
    <span>${reading.period()}</span>
    <span title="${reading.corrigenda()}">${reading.made()}</span>
    @if(reading.asked() != null)
      <b>${reading.asked()}</b>
    @endif
    @for(var lag : reading.lags())
      <span class="lag-warning">${lag}</span>
    @endfor
    <form class="on" method="get" action="${reading.action()}">
      <label for="on">or the version that applied on</label>
      <input id="on" name="on" type="date" value="${reading.on()}" min="${reading.first()}" required>
      <button>Go</button>
    </form>
  </div>
  @if(reading.compare() != null)
    <a class="button" href="${reading.compare()}">Compare with the version before</a>
  @endif
</section>
