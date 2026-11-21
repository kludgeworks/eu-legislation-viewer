@import dev.groknull.kludgeworks.eu.legislation.viewer.content.Element
@import dev.groknull.kludgeworks.eu.legislation.viewer.legaltext.LegalRenderer
@param Element node
@param LegalRenderer html
<%-- Shows what amended an occurrence, with the ▼ mark that EUR-Lex uses. The mark is in the margin beside
     the occurrence's number. Its hover says what was done and by which act. On the Read page, a click opens
     the history of the provision under it (<amendment-mark>, js/markers.js). --%><amendment-mark class="provenance" role="button" tabindex="0" data-act="${node.attribute("amendingAct")}" data-action="${node.attribute("action")}" title="${node.attribute("action")} by ${node.attribute("amendingAct")}"></amendment-mark>