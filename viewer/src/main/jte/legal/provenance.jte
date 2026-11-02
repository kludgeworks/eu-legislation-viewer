@import dev.groknull.kludgeworks.eu.legislation.viewer.content.Element
@import dev.groknull.kludgeworks.eu.legislation.viewer.content.web.LegalRenderer
@param Element node
@param LegalRenderer html
<span class="provenance" data-act="${node.attribute("amendingAct")}" data-action="${node.attribute("action")}" title="${node.attribute("action")} by ${node.attribute("amendingAct")}"></span>