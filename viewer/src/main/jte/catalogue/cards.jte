@import dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.web.view.CompareView
@param CompareView compare
<%-- A comparison's From and To cards (CompareView), each with a GET form for the version on another
     date at that end, the other end kept; and the acts between them --%>
<section class="selection compare" aria-label="Versions compared">
  @for(var card : java.util.List.of(compare.from(), compare.to()))
    <div class="card">
      <span class="card-kind">${card.kind()}</span>
      <a class="card-celex mono" href="${card.eurLex()}" title="On EUR-Lex">${card.celex()}</a>
      <span>${card.period()}</span>
      <span title="${card.corrigenda()}">${card.made()}</span>
      @for(var lag : card.lags())
        <span class="lag-warning">${lag}</span>
      @endfor
      <form class="on" method="get" action="${card.action()}">
        <label for="${card.name()}">or the version on</label>
        <input id="${card.name()}" name="${card.name()}" type="date" value="${card.on()}" min="${card.first()}" required>
        <input type="hidden" name="${card.keep()}" value="${card.kept()}">
        <button>Go</button>
      </form>
    </div>
    @if(card == compare.from())
      <span class="arrow" aria-hidden="true">→</span>
    @endif
  @endfor
  <div class="between">
    <span class="card-kind">${compare.actsHead()}</span>
    <div class="chips">
      @for(var act : compare.acts())
        <span class="chip mono">${act}</span>
      @endfor
    </div>
  </div>
</section>
