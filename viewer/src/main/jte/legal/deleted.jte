@import dev.groknull.kludgeworks.eu.legislation.viewer.content.Element
@import dev.groknull.kludgeworks.eu.legislation.viewer.legaltext.LegalRenderer
@param Element node
@param LegalRenderer html
<%-- Shows only the mark of what an amendment deleted, as EUR-Lex does. Read mode does not show the
     change. Compare mode does. The mark is a block's provenance, or it is made from the deleting
     act's own attributes. --%>@if(html.has(node, "provenance"))<div class="deleted">${html.children(node, "provenance")}</div>@else<span class="deleted"><amendment-mark class="provenance" role="link" tabindex="0" data-act="${node.attribute("amendingAct")}" data-action="${node.attribute("action")}" title="${node.attribute("action")} by ${node.attribute("amendingAct")}"></amendment-mark></span>@endif