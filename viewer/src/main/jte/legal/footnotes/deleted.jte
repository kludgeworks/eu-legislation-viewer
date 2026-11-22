@import dev.groknull.kludgeworks.eu.legislation.viewer.content.Element
@import dev.groknull.kludgeworks.eu.legislation.viewer.legaltext.LegalRenderer
@param Element node
@param LegalRenderer html
<%-- Holds the footnotes of deleted text. They are hidden, as the deleted text is. A footnote shows only
     when the reader follows a reference to it. --%><div class="deleted-footnotes">${html.others(node, "tag")}</div>