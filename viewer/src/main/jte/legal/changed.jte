@import dev.groknull.kludgeworks.eu.legislation.viewer.content.Element
@import dev.groknull.kludgeworks.eu.legislation.viewer.legaltext.LegalRenderer
@param Element node
@param LegalRenderer html
<ins class="changed" data-act="${node.attribute("amendingAct")}" data-modification="${node.attribute("modification")}">${html.children(node)}</ins>