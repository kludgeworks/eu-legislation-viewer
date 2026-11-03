@import dev.groknull.kludgeworks.eu.legislation.viewer.content.Element
@import dev.groknull.kludgeworks.eu.legislation.viewer.content.web.LegalRenderer
@param Element node
@param LegalRenderer html
@if(html.has(node, "num", "heading", "subheading"))<tr class="row-group-heading"><th colspan="${html.columns(node)}">${html.children(node, "num", "heading", "subheading")}</th></tr>@endif${html.children(node, "tr", "rowGroup")}