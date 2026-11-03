@import dev.groknull.kludgeworks.eu.legislation.viewer.content.Element
@import dev.groknull.kludgeworks.eu.legislation.viewer.content.web.LegalRenderer
@param Element node
@param LegalRenderer html
<%-- a definition list's items each print its separator between term and definition --%>@if(node.attribute("separator") != null)<div class="list list-${node.attribute("kind")}">@for(var child : node.children())@if(child.name().equals("unit"))@template.legal.list.entry(node = child, html = html, separator = node.attribute("separator"))@else${html.element(child)}@endif@endfor</div>@else<div class="list list-${node.attribute("kind")}">${html.children(node)}</div>@endif