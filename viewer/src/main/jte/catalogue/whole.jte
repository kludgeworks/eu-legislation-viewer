@import dev.groknull.kludgeworks.eu.legislation.viewer.comparison.web.view.ChangesView
@import dev.groknull.kludgeworks.eu.legislation.viewer.comparison.web.view.OutlineView
@import dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.web.view.CompareView
@import java.util.List
@param ChangesView changes
@param OutlineView outline
@param gg.jte.Content text
@param List<CompareView.ViewLink> views
@param CompareView.Only only = null
@param boolean corrected = false
<%-- A comparison's Whole text view (Design: Mode: Compare, Whole text): its controls, then every
     provision of the later text, each that differs marked in place, numbered, with its sidenote, a
     deleted one where it stood (text); and a pager to each difference. Beside it, the later text's
     outline, its differences counted --%>
<div class="outlined">
<nav class="outline counted" aria-label="Outline">
  <p class="outline-head"><span>Outline</span><span>differences</span></p>
  @template.comparison.outline(entries = outline.entries())
</nav>
<div class="changes whole">
  @template.catalogue.controls(changes = changes, views = views, only = only, corrected = corrected)
  <div class="book">${text}</div>
  @if(!changes.numbers().isEmpty())
    <nav class="pager" aria-label="Differences">@for(var number : changes.numbers())<a href="#difference-${number}">${number}</a>@endfor</nav>
  @endif
</div>
</div>
