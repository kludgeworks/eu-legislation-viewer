@import dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.ComparePage
@import dev.groknull.kludgeworks.eu.legislation.viewer.comparison.web.view.SidenotePieces
@import dev.groknull.kludgeworks.eu.legislation.viewer.comparison.web.view.DifferencesView
@import dev.groknull.kludgeworks.eu.legislation.viewer.comparison.web.view.SidenoteView
@param DifferencesView.Shown shown
@param SidenoteView sidenote = null
@param gg.jte.Content occurrence
@param ComparePage page
@param SidenotePieces pieces = null
<%-- Shows a provision that differs, as the Changes and Whole text views show it. It has a number,
     its occurrence as it reads with its edits (occurrence), and a sidenote beside it (sidenote.jte).
     It has an amber bar
     when a change does not apply on the later day of the comparison yet. Its edits are a
     correction's when corrigenda alone made it. An annex inserted whole is a box that shows its
     size and loads its outline or the annex itself when the reader asks (annexfold.jte). --%>
<section class="difference difference-${shown.kind()}${sidenote != null && sidenote.corrected() ? " corrected" : ""}${sidenote != null && sidenote.prospective() != null ? " prospective" : ""}" id="difference-${shown.number()}">
  <sup class="difference-number">${shown.number()}</sup>
  <aside class="sidenote">@template.comparison.sidenote(shown = shown, sidenote = sidenote, pieces = pieces)</aside>
  @if(shown.annexInserted())
    @template.comparison.annexfold(shown = shown, page = page)
  @else
    ${occurrence}
  @endif
</section>
