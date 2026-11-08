@import dev.groknull.kludgeworks.eu.legislation.viewer.content.Element
@import dev.groknull.kludgeworks.eu.legislation.viewer.legaltext.LegalRenderer
@param Element node
@param LegalRenderer html
<%-- Shows one of the files of an annex that the OJ prints across several files. The file is under
     the title that it repeats, and its notes come after it. --%><div class="file">${html.children(node)}</div>
