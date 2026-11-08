@import dev.groknull.kludgeworks.eu.legislation.viewer.content.Element
@import dev.groknull.kludgeworks.eu.legislation.viewer.legaltext.LegalRenderer
@param Element node
@param LegalRenderer html
@if("true".equals(node.attribute("header")))<th colspan="${node.attribute("colspan")}" rowspan="${node.attribute("rowspan")}">${html.children(node)}</th>@else<td colspan="${node.attribute("colspan")}" rowspan="${node.attribute("rowspan")}">${html.children(node)}</td>@endif