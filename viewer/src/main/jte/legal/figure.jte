@import dev.groknull.kludgeworks.eu.legislation.viewer.content.Element
@import dev.groknull.kludgeworks.eu.legislation.viewer.content.web.LegalRenderer
@param Element node
@param LegalRenderer html
<figure class="figure" data-src="${node.attribute("src")}" data-content="${node.attribute("content")}">${html.children(node)}</figure>