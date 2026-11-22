@import dev.groknull.kludgeworks.eu.legislation.viewer.comparison.web.view.SidenotePieces
@import dev.groknull.kludgeworks.eu.legislation.viewer.comparison.web.view.DifferencesView
@import dev.groknull.kludgeworks.eu.legislation.viewer.comparison.web.view.OutlineView
@import dev.groknull.kludgeworks.eu.legislation.viewer.comparison.web.view.ControlsView
@import java.util.List
@param DifferencesView differences
@param OutlineView outline
@param gg.jte.Content text
@param SidenotePieces pieces
@param List<ControlsView.ViewLink> views
@param ControlsView.Legend legend
<%-- Shows a comparison's Whole text view. The controls come first. Then the text shows every
     provision of the later text (text). Each provision that differs shows its edits in place, with a
     number and its sidenote. A deleted provision shows where it was. The later text's outline is
     beside the view, with a count of differences for each occurrence. --%>
<div class="outlined">
<act-outline>
<nav class="outline counted" aria-label="Outline">
  @template.comparison.outlinehead(total = differences.numbers().size())
  @template.comparison.outlinerail(outline = outline, total = differences.numbers().size())
  @template.comparison.outline(entries = outline.entries())
</nav>
</act-outline>
<div class="differences whole">
  @template.comparison.sidenotepieces(pieces = pieces)
  @template.comparison.controls(differences = differences, views = views, legend = legend)
  <div class="book">${text}</div>
</div>
</div>
