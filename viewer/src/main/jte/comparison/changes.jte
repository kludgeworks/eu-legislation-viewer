@import dev.groknull.kludgeworks.eu.legislation.viewer.comparison.web.view.SidenoteParts
@import dev.groknull.kludgeworks.eu.legislation.viewer.comparison.web.view.ChangesView
@import dev.groknull.kludgeworks.eu.legislation.viewer.comparison.web.view.OutlineView
@import dev.groknull.kludgeworks.eu.legislation.viewer.comparison.web.view.ControlsView
@import dev.groknull.kludgeworks.eu.legislation.viewer.comparison.web.view.SidenoteView
@import dev.groknull.kludgeworks.eu.legislation.viewer.legaltext.LegalRenderer
@import dev.groknull.kludgeworks.eu.legislation.viewer.comparepage.ComparePage
@import java.util.List
@import java.util.Map
@param ChangesView changes
@param OutlineView outline
@param Map<Integer, SidenoteView> sidenotes
@param SidenoteParts parts
@param LegalRenderer html
@param String units
@param ComparePage page
@param List<ControlsView.ViewLink> views
@param ControlsView.Only only = null
<%-- Shows a comparison's Changes view on an act's page. The controls come first. They show how
     many differences there are and of what kind. Then each provision that differs shows as it reads,
     with its changes marked and a number beside it. A sidenote beside each provision says what
     happened, which acts made the change and when each applies. It also says what the provision was
     before. The sidenotes map is keyed by difference number.
     Units before a difference that are the same in both texts are in a fold. When the reader opens
     the fold, htmx loads them from the content module through the units address, by eId. In a
     changed table, runs of rows that are the same in both texts are folded too, and an annex
     inserted whole shows only its size. These load from the difference's own address (page) when
     the reader opens them. The
     opening of the unit that holds a difference shows as context. The later text's outline is beside
     the view, with a count of differences for each unit. If the view is limited to one act's changes
     (only), a banner says so and links to the changes of all acts. --%>
<div class="outlined">
<act-outline>
<nav class="outline counted" aria-label="Outline">
  @template.comparison.outlinehead(total = changes.numbers().size())
  @template.comparison.outline(entries = outline.entries())
</nav>
</act-outline>
<div class="changes">
  @template.comparison.sidenoteparts(parts = parts)
  @template.comparison.controls(changes = changes, views = views, only = only, corrected = sidenotes.values().stream().anyMatch(SidenoteView::corrected))
  <div class="book legal-text legal-text-consolidated">
    @for(var passage : changes.passages())
      @if(passage instanceof ChangesView.Fold fold)
        <details class="fold" hx-get="${units}?${fold.query()}" hx-trigger="toggle once" hx-target="find .fold-text" hx-swap="innerHTML">
          <summary>${fold.label()} — ${fold.note()}<span class="show"> · <u>show</u></span></summary>
          <div class="fold-text"><p class="status">Loading…</p></div>
        </details>
      @elseif(passage instanceof ChangesView.Lead lead)
        <div class="lead">${html.element(lead.unit())}</div>
      @elseif(passage instanceof ChangesView.Shown shown)
        !{var sidenote = sidenotes.get(shown.number());}
        @template.comparison.difference(shown = shown, sidenote = sidenote, greyed = only != null && sidenote != null && !sidenote.candidates().isEmpty(), unit = html.element(shown.folded(page)), part = shown.part(page), parts = parts)
      @endif
    @endfor
  </div>
</div>
</div>
