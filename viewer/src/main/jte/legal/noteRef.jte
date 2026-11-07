@import dev.groknull.kludgeworks.eu.legislation.viewer.content.Element
@import dev.groknull.kludgeworks.eu.legislation.viewer.content.web.LegalRenderer
@param Element node
@param LegalRenderer html
<%-- Shows a note reference, as EUR-Lex does. A space sets it apart from the word before it. Only
     its number is raised. Example: "2320/2002 (¹)". --%> <a class="note-ref" href="${node.attribute("href")}">(<sup>${node.attribute("marker")}</sup>)</a>