@import dev.groknull.kludgeworks.eu.legislation.viewer.content.Element
@import dev.groknull.kludgeworks.eu.legislation.viewer.content.web.LegalRenderer
@param Element node
@param LegalRenderer html
<div class="unit unit-${node.attribute("kind")}" id="${node.attribute("eId")}" data-depth="${node.attribute("depth")}">${html.children(node)}</div>