@import dev.groknull.kludgeworks.eu.legislation.viewer.content.Element
@import dev.groknull.kludgeworks.eu.legislation.legaltext.LegalRenderer
@param Element node
@param LegalRenderer html
<blockquote class="quoted-structure" id="${node.attribute("eId")}">${html.children(node)}</blockquote>