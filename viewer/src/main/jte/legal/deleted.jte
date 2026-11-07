@import dev.groknull.kludgeworks.eu.legislation.viewer.content.Element
@import dev.groknull.kludgeworks.eu.legislation.viewer.content.web.LegalRenderer
@param Element node
@param LegalRenderer html
<%-- what an amendment deleted: its mark only, as EUR-Lex has it (Design D-20: Read doesn't show the
change; Compare does): a block's provenance, or one of the deleting act's own attributes --%>@if(html.has(node, "provenance"))<div class="deleted">${html.children(node, "provenance")}</div>@else<span class="deleted"><span class="provenance" role="link" tabindex="0" data-act="${node.attribute("amendingAct")}" data-action="${node.attribute("action")}" title="${node.attribute("action")} by ${node.attribute("amendingAct")}"></span></span>@endif