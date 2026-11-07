@import dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.web.view.CompareView
@param CompareView compare
<%-- Shows a comparison's From and To cards, and then the acts between them. CompareView supplies
     the data. Each card has a GET form that asks for the version on another date at that end. The
     form keeps the other end as it is. --%>
<section class="selection compare" aria-label="Versions compared">
  @for(var card : java.util.List.of(compare.from(), compare.to()))
    <div class="card">
      <span class="card-kind">${card.kind()}</span>
      <a class="card-celex mono" href="${card.eurLex()}" title="On EUR-Lex">${card.celex()}</a>
      <span>${card.period()}</span>
      <span title="${card.published()}">${card.made()}</span>
      @for(var lag : card.lags())
        <span class="lag-warning">${lag}</span>
      @endfor
      @for(var lag : card.mayLag())
        <span class="lag-maybe">${lag}</span>
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
  @template.catalogue.acts(acts = compare.acts())
</section>
