@import dev.groknull.kludgeworks.eu.legislation.viewer.comparison.web.view.SidenotePieces
@import dev.groknull.kludgeworks.eu.legislation.viewer.comparison.web.view.DifferencesView
@import dev.groknull.kludgeworks.eu.legislation.viewer.comparison.web.view.OutlineView
@import dev.groknull.kludgeworks.eu.legislation.viewer.comparison.web.view.ControlsView
@import dev.groknull.kludgeworks.eu.legislation.viewer.comparison.web.view.SidenoteView
@import dev.groknull.kludgeworks.eu.legislation.viewer.legaltext.LegalRenderer
@import dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.ComparePage
@import java.util.List
@import java.util.Map
@param DifferencesView differences
@param OutlineView outline
@param Map<Integer, SidenoteView> sidenotes
@param SidenotePieces pieces
@param LegalRenderer html
@param String occurrences
@param ComparePage page
@param List<ControlsView.ViewLink> views
<%-- Shows a comparison's Differences view on an act's page. The controls come first. They show how
     many differences there are and of what kind. Then each provision that differs shows as it reads,
     with its edits and a number beside it. A sidenote beside each provision says what
     happened, which acts made the change and when each applies. It also says what the provision was
     before. The sidenotes map is keyed by difference number.
     Occurrences before a difference that are the same in both texts are in a fold. When the reader opens
     the fold, htmx loads them from the content module through the occurrences address, by eId. In a
     changed table, runs of rows that are the same in both texts are folded too, and an annex
     inserted whole shows only its size. These load from the difference's own address (page) when
     the reader opens them. The
     opening of the occurrence that holds a difference shows as context. The later text's outline is beside
     the view, with a count of differences for each occurrence. Compare has no act filter: the view shows the
     changes of all acts. --%>
<div class="outlined">
<act-outline>
<nav class="outline counted" aria-label="Outline">
  @template.comparison.outlinehead(total = differences.numbers().size())
  @template.comparison.outlinerail(outline = outline, total = differences.numbers().size())
  @template.comparison.outline(entries = outline.entries())
</nav>
</act-outline>
<div class="differences">
  @template.comparison.sidenotepieces(pieces = pieces)
  @template.comparison.controls(differences = differences, views = views, legend = ControlsView.legend(differences, sidenotes))
  <div class="book legal-text legal-text-consolidated">
    @for(var passage : differences.passages())
      @if(passage instanceof DifferencesView.Fold fold)
        <details class="fold" hx-get="${occurrences}?${fold.query()}" hx-trigger="toggle once" hx-target="find .fold-text" hx-swap="innerHTML">
          <summary>${fold.label()} — ${fold.reason()}<span class="show"> · <u>show</u></span></summary>
          <div class="fold-text"><p class="status">Loading…</p></div>
        </details>
      @elseif(passage instanceof DifferencesView.Lead lead)
        <div class="lead">${html.element(lead.occurrence())}</div>
      @elseif(passage instanceof DifferencesView.Shown shown)
        !{var sidenote = sidenotes.get(shown.number());}
        @template.comparison.difference(shown = shown, sidenote = sidenote, occurrence = (shown.kind().equals("deleted") ? html.deleted() : html).element(shown.folded(page)), page = page, pieces = pieces)
      @endif
    @endfor
  </div>
</div>
</div>
