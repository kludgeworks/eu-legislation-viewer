@import dev.groknull.kludgeworks.eu.legislation.viewer.content.Element
@import dev.groknull.kludgeworks.eu.legislation.viewer.content.web.LegalRenderer
@param Element node
@param LegalRenderer html
<%-- the notes of deleted text: out of sight, as that text is, but for one its reference was followed to --%><div class="deleted-notes">${html.others(node, "provenance")}</div>