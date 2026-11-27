@import dev.groknull.kludgeworks.eu.legislation.viewer.content.Element
@import dev.groknull.kludgeworks.eu.legislation.viewer.legaltext.LegalRenderer
@param Element node
@param LegalRenderer html
!{var preview = html.imagePreviewUrl(node.attribute("src"));}<figure class="figure" data-src="${node.attribute("src")}" data-content="${node.attribute("content")}">@if(preview != null)<img src="${preview}" alt="${html.imageAlt(node)}" loading="lazy" decoding="async">@endif${html.children(node)}</figure>