@import dev.groknull.kludgeworks.eu.legislation.viewer.content.Element
@import dev.groknull.kludgeworks.eu.legislation.viewer.legaltext.LegalRenderer
@param Element node
@param LegalRenderer html
<%-- a table's notes, under it as EUR-Lex prints them --%><aside class="notes table-notes">${html.children(node)}</aside>