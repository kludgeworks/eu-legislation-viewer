@import dev.groknull.kludgeworks.eu.legislation.viewer.content.Element
@import dev.groknull.kludgeworks.eu.legislation.legaltext.LegalRenderer
@param Element node
@param LegalRenderer html
<span class="quote">${node.attribute("startQuote")}${html.children(node)}${node.attribute("endQuote")}</span>