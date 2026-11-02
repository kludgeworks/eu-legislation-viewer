@import dev.groknull.kludgeworks.eu.legislation.viewer.content.Element
@import dev.groknull.kludgeworks.eu.legislation.viewer.content.web.LegalRenderer
@param Element node
@param LegalRenderer html
<a class="ref ref-${node.attribute("kind")}" href="${node.attribute("href")}">${html.children(node)}</a>