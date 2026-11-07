@import dev.groknull.kludgeworks.eu.legislation.viewer.content.Element
@import dev.groknull.kludgeworks.eu.legislation.viewer.content.web.LegalRenderer
@param Element node
@param LegalRenderer html
<%-- Shows a table in a frame. The frame scrolls a wide table, and assistive technology still reads
     it as a table. The table's notes are under it, outside the frame. --%><div class="table-frame"><table class="table">${html.others(node, "notes")}</table></div>${html.children(node, "notes")}