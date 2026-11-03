@import dev.groknull.kludgeworks.eu.legislation.viewer.content.Element
@import dev.groknull.kludgeworks.eu.legislation.viewer.content.web.LegalRenderer
@param Element node
@param LegalRenderer html
<%-- one of the files of an annex the OJ prints across several: under the title it repeats, its notes after it --%><div class="file">${html.children(node)}</div>
