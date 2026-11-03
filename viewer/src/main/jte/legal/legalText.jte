@import dev.groknull.kludgeworks.eu.legislation.viewer.content.Element
@import dev.groknull.kludgeworks.eu.legislation.viewer.content.web.JournalHeader
@import dev.groknull.kludgeworks.eu.legislation.viewer.content.web.LegalRenderer
@param Element node
@param LegalRenderer html
<article class="legal-text legal-text-${node.attribute("kind")}" lang="${node.attribute("xml:lang")}">!{var header = JournalHeader.of(node);}@if(header != null)@template.legalpart.journalHeader(header = header)@endif${html.children(node)}</article>