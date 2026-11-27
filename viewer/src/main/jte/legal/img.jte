@import dev.groknull.kludgeworks.eu.legislation.viewer.content.Element
@import dev.groknull.kludgeworks.eu.legislation.viewer.legaltext.LegalRenderer
@param Element node
@param LegalRenderer html
!{var preview = html.imagePreviewUrl(node.attribute("src"));}@if(preview != null)<img class="img" src="${preview}" data-src="${node.attribute("src")}" data-content="${node.attribute("content")}" alt="${html.imageAlt(node)}" loading="lazy" decoding="async">@else<span class="img" data-src="${node.attribute("src")}" data-content="${node.attribute("content")}"></span>@endif