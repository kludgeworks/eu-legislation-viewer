@import dev.groknull.kludgeworks.eu.legislation.viewer.content.Element
@import dev.groknull.kludgeworks.eu.legislation.legaltext.LegalRenderer
@param Element node
@param LegalRenderer html
<section class="preamble" id="${node.attribute("eId")}">${html.children(node)}</section>