@import dev.groknull.kludgeworks.eu.legislation.viewer.comparison.web.view.NeighbourView
@import dev.groknull.kludgeworks.eu.legislation.viewer.legaltext.LegalRenderer
@import java.util.List
@param List<NeighbourView> rows
@param LegalRenderer html
<%-- Shows the occurrences of a Synopsis fold when the reader opens it in place. Each occurrence is a row and
     is the same in both texts. --%>
@for(var row : rows)
  @template.comparison.neighbour(occurrence = row.occurrence(), element = row.element(), history = row.history(), html = html)
@endfor
