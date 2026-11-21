@import dev.groknull.kludgeworks.eu.legislation.viewer.content.Element
@import dev.groknull.kludgeworks.eu.legislation.viewer.legaltext.LegalRenderer
@param Element node
@param LegalRenderer html
<%-- Shows only the tag of what an amendment deleted, as EUR-Lex does. Read mode does not show the
     change. Compare mode does. The tag is a block's own tag, or it is made from the deleting
     act's own attributes. --%>@if(html.has(node, "tag"))<div class="deleted">${html.children(node, "tag")}</div>@else<span class="deleted"><text-tag class="tag" role="button" tabindex="0" data-act="${node.attribute("amendingAct")}" data-action="${node.attribute("action")}" title="${node.attribute("action")} by ${node.attribute("amendingAct")}"></text-tag></span>@endif