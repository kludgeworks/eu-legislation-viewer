@import dev.groknull.kludgeworks.eu.legislation.viewer.content.Element
@import dev.groknull.kludgeworks.eu.legislation.viewer.legaltext.LegalRenderer
@param Element node
@param LegalRenderer html
<%-- Shows a list. In a definition list, each item prints the list's separator between its term
     and its definition. --%>@if(node.attribute("separator") != null)<div class="list list-${node.attribute("kind")}">@for(var child : node.children())@if(child.name().equals("unit"))@template.legalpart.definition(node = child, html = html, separator = node.attribute("separator"))@else${html.element(child)}@endif@endfor</div>@else<div class="list list-${node.attribute("kind")}">${html.children(node)}</div>@endif