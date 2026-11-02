@import dev.groknull.kludgeworks.eu.legislation.viewer.content.Element
@import dev.groknull.kludgeworks.eu.legislation.viewer.content.web.LegalRenderer
@param Element node
@param LegalRenderer html
<div class="block-container emphasis-${node.attribute("emphasis")}" lang="${node.attribute("xml:lang")}">${html.children(node)}</div>