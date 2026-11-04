@import dev.groknull.kludgeworks.eu.legislation.viewer.content.Element
@import dev.groknull.kludgeworks.eu.legislation.viewer.content.web.LegalRenderer
@param Element node
@param LegalRenderer html
<%-- the frame scrolls a wide table, which stays a table to assistive technology; its notes under it, outside the frame --%><div class="table-frame"><table class="table">${html.others(node, "notes")}</table></div>${html.children(node, "notes")}