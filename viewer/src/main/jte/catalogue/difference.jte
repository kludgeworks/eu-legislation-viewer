@import dev.groknull.kludgeworks.eu.legislation.viewer.comparison.web.view.ChangesView
@import dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.web.view.SidenoteView
@param ChangesView.Shown shown
@param SidenoteView sidenote = null
@param boolean greyed = false
@param gg.jte.Content unit
<%-- A provision that differs, as the Changes and Whole text views show it: numbered, its unit as it
     reads, marked (unit), with a sidenote beside it (sidenote.jte). Greyed where it may be the
     filter's act's change, not known; its marks a correction's where corrigenda alone made it; an annex
     inserted whole folded to its size (Design D-44) --%>
<section class="difference difference-${shown.kind()}${greyed ? " unknown" : ""}${sidenote != null && sidenote.corrected() ? " corrected" : ""}" id="difference-${shown.number()}">
  <sup class="difference-number">${shown.number()}</sup>
  <aside class="sidenote">@template.catalogue.sidenote(shown = shown, sidenote = sidenote)</aside>
  @if(shown.annexInserted())
    <details class="annex-inserted"><summary>Inserted whole · ${shown.size()} · <span class="show">show</span></summary>${unit}</details>
  @else
    ${unit}
  @endif
</section>
