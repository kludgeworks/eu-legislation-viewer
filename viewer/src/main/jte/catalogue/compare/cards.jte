@import dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.web.view.CompareView
@param CompareView compare
<%-- Shows a comparison's From and To cards, and then the slot of the acts between them. CompareView
     supplies the data. Each card is the law on a day: the day is the headline. Under it: which
     version that is and how long it applied, the acts that made it, and the version's consolidated
     CELEX, small, with a copy button. A day before the act's first version has a dash instead.
     Each card has a GET form that asks for the law on another day at that end. The form keeps the
     other end as it is.
     The acts are the comparison module's, with how many differences each made. htmx loads them
     from that module's address (compare.acts()) when the slot loads, and swaps the slot for them. --%>
<section class="selection compare" aria-label="Versions compared">
  @for(var card : java.util.List.of(compare.from(), compare.to()))
    <article class="card">
      <span class="card-kind">${card.kind()}</span>
      <span class="card-day">${card.day()}</span>
      @if(card.version() != null)
        <span>${card.version()}</span>
        <span title="${card.published()}">${card.made()}</span>
      @else
        <span>—</span>
      @endif
      @if(card.celex() != null)
        <copy-text class="card-celex" data-text="${card.celex()}"><a class="mono quiet" href="${card.eurLex()}" rel="external">${card.celex()}<span aria-hidden="true"> ↗</span><span class="visually-hidden"> (opens EUR-Lex)</span></a> <button type="button" class="link">copy</button></copy-text>
      @endif
      <form class="on" method="get" action="${card.action()}">
        <label for="${card.name()}">The law on</label>
        <input id="${card.name()}" name="${card.name()}" type="date" value="${card.on()}" required>
        <input type="hidden" name="${card.keep()}" value="${card.kept()}">
        <button>Go</button>
      </form>
    </article>
    @if(card == compare.from())
      <span class="arrow" aria-hidden="true">→</span>
    @endif
  @endfor
  @if(compare.acts() != null)
    <div class="between" id="acts" hx-get="${compare.acts()}" hx-trigger="load" hx-swap="outerHTML">
      <span class="card-kind">Acts between them</span>
      <noscript><p><a href="${compare.acts()}">The acts between them</a></p></noscript>
    </div>
  @endif
</section>
