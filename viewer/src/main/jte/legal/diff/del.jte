@import dev.groknull.kludgeworks.eu.legislation.viewer.content.Element
@import dev.groknull.kludgeworks.eu.legislation.viewer.content.web.LegalRenderer
@param Element node
@param LegalRenderer html
<%-- words a comparison found deleted, where they stood (Difference.shown): only ever around text --%><del class="diff">${html.children(node)}</del>