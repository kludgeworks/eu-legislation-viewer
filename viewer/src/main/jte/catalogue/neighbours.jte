@import dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.web.view.NeighbourView
@import dev.groknull.kludgeworks.eu.legislation.viewer.content.web.LegalRenderer
@import java.util.List
@param List<NeighbourView> rows
@param LegalRenderer html
<%-- Shows the units of a Synopsis fold when the reader opens it in place. Each unit is a row and
     is the same in both texts. --%>
@for(var row : rows)
  @template.catalogue.neighbour(unit = row.unit(), element = row.element(), history = row.history(), html = html)
@endfor
