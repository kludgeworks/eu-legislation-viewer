@import dev.groknull.kludgeworks.eu.legislation.viewer.content.Element
@import dev.groknull.kludgeworks.eu.legislation.legaltext.LegalRenderer
@param Element node
@param LegalRenderer html
<div class="note" id="${node.attribute("eId")}"><span class="note-marker">(${node.attribute("marker")})</span>${html.children(node)}</div>