@import dev.groknull.kludgeworks.eu.legislation.viewer.content.Element
@import dev.groknull.kludgeworks.eu.legislation.viewer.content.web.LegalRenderer
@param Element node
@param LegalRenderer html
<%-- Holds the notes of deleted text. They are hidden, as the deleted text is. A note shows only
     when the reader follows a reference to it. --%><div class="deleted-notes">${html.others(node, "provenance")}</div>