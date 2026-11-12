@import dev.groknull.kludgeworks.eu.legislation.viewer.comparepage.ComparePage
@import dev.groknull.kludgeworks.eu.legislation.viewer.comparison.web.view.SidenoteParts
@import dev.groknull.kludgeworks.eu.legislation.viewer.comparison.web.view.ChangesView
@import dev.groknull.kludgeworks.eu.legislation.viewer.comparison.web.view.SidenoteView
@param ChangesView.Shown shown
@param SidenoteView sidenote = null
@param boolean greyed = false
@param gg.jte.Content unit
@param ComparePage page
@param SidenoteParts parts = null
<%-- Shows a provision that differs, as the Changes and Whole text views show it. It has a number,
     its unit as it reads with the changes marked (unit), and a sidenote beside it (sidenote.jte).
     It is greyed when it is not known if the filter's act made the change. Its marks are a
     correction's when corrigenda alone made it. An annex inserted whole is a box that shows its
     size and loads its outline or the annex itself when the reader asks (annexfold.jte). --%>
<section class="difference difference-${shown.kind()}${greyed ? " unknown" : ""}${sidenote != null && sidenote.corrected() ? " corrected" : ""}" id="difference-${shown.number()}">
  <sup class="difference-number">${shown.number()}</sup>
  <aside class="sidenote">@template.comparison.sidenote(shown = shown, sidenote = sidenote, parts = parts)</aside>
  @if(shown.annexInserted())
    @template.comparison.annexfold(shown = shown, page = page)
  @else
    ${unit}
  @endif
</section>
