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
<%-- Shows a comparison's Whole text view. The controls come first. Then the text shows every
     provision of the later text (text). Each provision that differs is marked in place, with a
     number and its sidenote. A deleted provision shows where it was. The later text's outline is
     beside the view, with a count of differences for each unit. --%>
<div class="outlined">
<nav class="outline counted" aria-label="Outline">
  @template.catalogue.outlinehead(total = changes.numbers().size())
  @template.comparison.outline(entries = outline.entries())
</nav>
<div class="changes whole">
  @template.catalogue.controls(changes = changes, views = views, only = only, corrected = corrected)
  <div class="book">${text}</div>
</div>
</div>
