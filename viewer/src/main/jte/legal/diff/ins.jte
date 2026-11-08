@import dev.groknull.kludgeworks.eu.legislation.viewer.content.Element
@import dev.groknull.kludgeworks.eu.legislation.viewer.legaltext.LegalRenderer
@param Element node
@param LegalRenderer html
<%-- words a comparison found inserted (Difference.shown): around text, a formula or an image --%><ins class="diff">${html.children(node)}</ins>