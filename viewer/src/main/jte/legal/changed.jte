@import dev.groknull.kludgeworks.eu.legislation.viewer.content.Element
@import dev.groknull.kludgeworks.eu.legislation.legaltext.LegalRenderer
@param Element node
@param LegalRenderer html
<ins class="changed" data-act="${node.attribute("amendingAct")}" data-action="${node.attribute("action")}">${html.children(node)}</ins>