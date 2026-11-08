@import dev.groknull.kludgeworks.eu.legislation.viewer.content.Element
@import dev.groknull.kludgeworks.eu.legislation.viewer.legaltext.LegalRenderer
@param Element node
@param LegalRenderer html
<%-- rows of a changed table that are the same in both texts, folded (RowFolds). It spans the table's
     columns. When the reader opens it, htmx loads the rows from its address and puts them in its
     place. --%><tr class="rows-fold"><td colspan="${node.attribute("columns")}"><button type="button" hx-get="${node.attribute("href")}" hx-target="closest tr" hx-swap="outerHTML">${node.attribute("rows")} rows the same in both versions<span class="show"> · <u>show</u></span></button></td></tr>