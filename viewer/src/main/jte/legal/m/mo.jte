@import dev.groknull.kludgeworks.eu.legislation.viewer.content.Element
@import dev.groknull.kludgeworks.eu.legislation.viewer.content.web.LegalRenderer
@param Element node
@param LegalRenderer html
<mo fence="${node.attribute("fence")}" largeop="${node.attribute("largeop")}" accent="${node.attribute("accent")}">${node.text()}</mo>