@import dev.groknull.kludgeworks.eu.legislation.viewer.comparison.web.view.NeighbourView
@import dev.groknull.kludgeworks.eu.legislation.viewer.legaltext.LegalRenderer
@import java.util.List
@param List<NeighbourView> rows
@param LegalRenderer html
<%-- Shows the units of a Synopsis fold when the reader opens it in place. Each unit is a row and
     is the same in both texts. --%>
@for(var row : rows)
  @template.comparison.neighbour(unit = row.unit(), element = row.element(), history = row.history(), html = html)
@endfor
