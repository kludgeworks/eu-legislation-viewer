@import dev.groknull.kludgeworks.eu.legislation.viewer.content.Element
@import dev.groknull.kludgeworks.eu.legislation.viewer.legaltext.LegalRenderer
@param Element node
@param LegalRenderer html
<%-- Shows a footnote with its marker. In a comparison, a footnote that only one of the two texts has gets
     the class footnote-ins or footnote-del, so a column shows only the footnotes of its text. --%><div class="footnote${node.attribute("diff") == null ? "" : " footnote-" + node.attribute("diff")}" id="${node.attribute("eId")}"><span class="footnote-marker">(${node.attribute("marker")})</span>${html.children(node)}</div>