@import dev.groknull.kludgeworks.eu.legislation.viewer.comparison.web.view.ChangesView
@import dev.groknull.kludgeworks.eu.legislation.viewer.content.web.LegalRenderer
@param ChangesView changes
@param LegalRenderer html
@param String units
<%-- A comparison's Changes view (Design: Mode: Compare, Changes): how many differences and of what
     kind, then each provision that differs as it reads, marked, numbered beside it with a note of
     what happened; the units before it that are the same in both folded, loaded from the content
     module when opened (units, by eId); the opening of the unit it is in, as context; and a pager
     to each difference --%>
<div class="changes">
  <div class="controls">
    <b>${changes.count()}</b>
    @if(changes.breakdown() != null)<span>${changes.breakdown()}</span>@endif
  </div>
  <div class="book legal-text legal-text-consolidated">
    @for(var passage : changes.passages())
      @if(passage instanceof ChangesView.Fold fold)
        <details class="fold" hx-get="${units}?${fold.query()}" hx-trigger="toggle once" hx-target="find .fold-text" hx-swap="innerHTML">
          <summary>${fold.label()} — no differences<span class="show"> · <u>show</u></span></summary>
          <div class="fold-text"><p class="status">Loading…</p></div>
        </details>
      @elseif(passage instanceof ChangesView.Lead lead)
        <div class="lead">${html.element(lead.unit())}</div>
      @elseif(passage instanceof ChangesView.Shown shown)
        <section class="difference difference-${shown.kind()}" id="difference-${shown.number()}">
          <sup class="difference-number">${shown.number()}</sup>
          <aside class="sidenote"><b>${shown.number()}</b> ${shown.note()}</aside>
          ${html.element(shown.difference().shown())}
        </section>
      @endif
    @endfor
  </div>
  @if(!changes.numbers().isEmpty())
    <nav class="pager" aria-label="Differences">@for(var number : changes.numbers())<a href="#difference-${number}">${number}</a>@endfor</nav>
  @endif
</div>
