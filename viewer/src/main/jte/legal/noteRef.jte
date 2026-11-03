@import dev.groknull.kludgeworks.eu.legislation.viewer.content.Element
@import dev.groknull.kludgeworks.eu.legislation.viewer.content.web.LegalRenderer
@param Element node
@param LegalRenderer html
<%-- set apart from the word before, only its number raised, as EUR-Lex has it: 2320/2002 (¹) --%> <a class="note-ref" href="${node.attribute("href")}">(<sup>${node.attribute("marker")}</sup>)</a>