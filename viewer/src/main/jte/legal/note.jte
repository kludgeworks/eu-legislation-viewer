@import dev.groknull.kludgeworks.eu.legislation.viewer.content.Element
@import dev.groknull.kludgeworks.eu.legislation.viewer.legaltext.LegalRenderer
@param Element node
@param LegalRenderer html
<%-- Shows a note with its marker. In a comparison, a note that only one of the two texts has gets
     the class note-ins or note-del, so a column shows only the notes of its text. --%><div class="note${node.attribute("diff") == null ? "" : " note-" + node.attribute("diff")}" id="${node.attribute("eId")}"><span class="note-marker">(${node.attribute("marker")})</span>${html.children(node)}</div>