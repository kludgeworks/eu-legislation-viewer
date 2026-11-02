@import dev.groknull.kludgeworks.eu.legislation.viewer.content.Element
@import dev.groknull.kludgeworks.eu.legislation.viewer.content.web.LegalRenderer
@param Element node
@param LegalRenderer html
<a class="note-ref" href="${node.attribute("href")}"><sup>(${node.attribute("marker")})</sup></a>