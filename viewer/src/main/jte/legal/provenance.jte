@import dev.groknull.kludgeworks.eu.legislation.viewer.content.Element
@import dev.groknull.kludgeworks.eu.legislation.viewer.content.web.LegalRenderer
@param Element node
@param LegalRenderer html
<%-- what amended a unit, as EUR-Lex marks it: ▼, in the margin beside its number; its hover says what
     and which act, a click opens the change in Compare (Design D-20, js/markers.js) --%><span class="provenance" role="link" tabindex="0" data-act="${node.attribute("amendingAct")}" data-action="${node.attribute("action")}" title="${node.attribute("action")} by ${node.attribute("amendingAct")}"></span>