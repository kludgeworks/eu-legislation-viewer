@import dev.groknull.kludgeworks.eu.legislation.viewer.content.Element
@import dev.groknull.kludgeworks.eu.legislation.viewer.content.web.LegalRenderer
@param Element node
@param LegalRenderer html
<article class="legal-text legal-text-${node.attribute("kind")}" lang="${node.attribute("xml:lang")}">@if("original".equals(node.attribute("kind")))@template.legal.journalHeader(node = node, html = html)@endif${html.children(node)}</article>