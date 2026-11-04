@import dev.groknull.kludgeworks.eu.legislation.viewer.content.Element
@import dev.groknull.kludgeworks.eu.legislation.viewer.content.web.LegalRenderer
@param Element node
@param LegalRenderer html
<time datetime="${node.attribute("date")}">${html.children(node)}</time>