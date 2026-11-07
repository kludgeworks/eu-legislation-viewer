@import dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.web.view.ReadingView
@param ReadingView reading
<%-- Shows the Reading card. ReadingView supplies the data. The card shows the version, its period,
     what made it and the date asked. It warns in amber where the text lags the law, and in grey
     where it may lag. A GET form asks for the version on another date. A link goes to the
     comparison with the version before. For the first version, the link goes to the comparison
     with the version after. --%>
<section class="selection" aria-label="Reading">
  <div class="card">
    <span class="card-kind">Reading</span>
    <a class="card-celex mono" href="${reading.eurLex()}" title="On EUR-Lex">${reading.celex()}</a>
    <span>${reading.period()}</span>
    <span title="${reading.published()}">${reading.made()}</span>
    @if(reading.asked() != null)
      <b>${reading.asked()}</b>
    @endif
    @for(var lag : reading.lags())
      <span class="lag-warning">${lag}</span>
    @endfor
    @for(var lag : reading.mayLag())
      <span class="lag-maybe">${lag}</span>
    @endfor
    <form class="on" method="get" action="${reading.action()}">
      <label for="on">or the version that applied on</label>
      <input id="on" name="on" type="date" value="${reading.on()}" min="${reading.first()}" required>
      <button>Go</button>
    </form>
  </div>
  @if(reading.compare() != null)
    <a class="button" href="${reading.compare()}">${reading.comparing()}</a>
  @endif
</section>
