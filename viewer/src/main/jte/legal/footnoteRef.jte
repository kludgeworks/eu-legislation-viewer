@import dev.groknull.kludgeworks.eu.legislation.viewer.content.Element
@import dev.groknull.kludgeworks.eu.legislation.viewer.legaltext.LegalRenderer
@param Element node
@param LegalRenderer html
<%-- Shows a footnote reference, as EUR-Lex does. A space sets it apart from the word before it. Only
     its number is raised. Example: "2320/2002 (¹)". In a comparison, a reference that only one of
     the two texts has gets the class footnote-ins or footnote-del. --%> <a class="footnote-ref${node.attribute("diff") == null ? "" : " footnote-" + node.attribute("diff")}" href="${node.attribute("href")}">(<sup>${node.attribute("marker")}</sup>)</a>