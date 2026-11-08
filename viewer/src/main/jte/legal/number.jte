@import dev.groknull.kludgeworks.eu.legislation.viewer.content.Element
@import dev.groknull.kludgeworks.eu.legislation.viewer.legaltext.LegalRenderer
@param Element node
@param LegalRenderer html
<%-- a number as EUR-Lex prints it: its thousands set apart (6 000) --%>${html.thousands(node.attribute("v"))}