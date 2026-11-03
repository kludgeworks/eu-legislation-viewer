@import dev.groknull.kludgeworks.eu.legislation.viewer.content.Element
@import dev.groknull.kludgeworks.eu.legislation.viewer.content.web.LegalRenderer
@param Element node
@param LegalRenderer html
<article class="legal-text legal-text-${node.attribute("kind")}" lang="${node.attribute("xml:lang")}">${html.children(node)}</article>