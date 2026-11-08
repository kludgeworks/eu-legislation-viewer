@import dev.groknull.kludgeworks.eu.legislation.viewer.content.Element
@import dev.groknull.kludgeworks.eu.legislation.legaltext.LegalRenderer
@param Element node
@param LegalRenderer html
<mi mathvariant="${node.attribute("mathvariant")}">${node.text()}</mi>