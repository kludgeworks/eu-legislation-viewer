@import dev.groknull.kludgeworks.eu.legislation.viewer.comparison.web.view.ChangesView
@import dev.groknull.kludgeworks.eu.legislation.viewer.comparison.web.view.OutlineView
@import dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.web.view.CompareView
@import dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.web.view.SidenoteView
@import dev.groknull.kludgeworks.eu.legislation.viewer.content.web.LegalRenderer
@import java.util.List
@import java.util.Map
@param ChangesView changes
@param OutlineView outline
@param Map<Integer, SidenoteView> sidenotes
@param LegalRenderer html
@param String units
@param List<CompareView.ViewLink> views
@param CompareView.Only only = null
@param String preamble = ""
<%-- A comparison's Changes view on an act's page (Design: Mode: Compare, Changes): its controls,
     how many differences and of what kind, then each provision that differs as it reads, marked, numbered
     beside it with a sidenote: what happened, which acts made it and when each applies, and what it
     was before (sidenotes, by number); the units before it that are the same in both folded, loaded
     from the content module when opened (units, by eId); the opening of the unit it is in, as
     context; and a pager to each difference. Beside it, the later text's outline, its differences
     counted. Limited to one act's changes (only), a banner says so, linking to all acts' --%>
<div class="outlined">
<nav class="outline counted" aria-label="Outline">
  <p class="outline-head"><span>Outline</span><span>differences</span></p>
  @template.comparison.outline(entries = outline.entries())
</nav>
<div class="changes">
  @template.catalogue.controls(changes = changes, views = views, only = only)
  <div class="book legal-text legal-text-consolidated">
    @for(var passage : changes.passages())
      @if(passage instanceof ChangesView.Fold fold)
        <details class="fold" hx-get="${units}?${fold.query()}" hx-trigger="toggle once" hx-target="find .fold-text" hx-swap="innerHTML">
          <summary>${fold.label()} — ${fold.note()}<span class="show"> · <u>show</u></span></summary>
          <div class="fold-text"><p class="status">Loading…</p></div>
        </details>
      @elseif(passage instanceof ChangesView.NoPreamble)
        <p class="fold no-preamble">Preamble — ${preamble}</p>
      @elseif(passage instanceof ChangesView.Lead lead)
        <div class="lead">${html.element(lead.unit())}</div>
      @elseif(passage instanceof ChangesView.Shown shown)
        !{var sidenote = sidenotes.get(shown.number());}
        @template.catalogue.difference(shown = shown, sidenote = sidenote, greyed = only != null && sidenote != null && !sidenote.candidates().isEmpty(), unit = html.element(shown.unit()))
      @endif
    @endfor
  </div>
  @if(!changes.numbers().isEmpty())
    <nav class="pager" aria-label="Differences">@for(var number : changes.numbers())<a href="#difference-${number}">${number}</a>@endfor</nav>
  @endif
</div>
</div>
