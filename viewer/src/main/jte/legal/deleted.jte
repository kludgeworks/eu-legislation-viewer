@import dev.groknull.kludgeworks.eu.legislation.viewer.content.Element
@import dev.groknull.kludgeworks.eu.legislation.viewer.content.web.LegalRenderer
@param Element node
@param LegalRenderer html
<%-- what an amendment deleted, hidden behind its mark until the mark is clicked: a block (its
provenance, the deleting act, first) or words in running text (the act its own attributes) --%>@if(html.has(node, "provenance"))<div class="deleted"><label class="deleted-mark"><input type="checkbox">${html.children(node, "provenance")}</label><del>${html.others(node, "provenance")}</del></div>@else<span class="deleted"><label class="deleted-mark"><input type="checkbox"><span class="provenance" data-act="${node.attribute("amendingAct")}" data-action="${node.attribute("action")}" title="${node.attribute("action")} by ${node.attribute("amendingAct")}"></span></label><del>${html.children(node)}</del></span>@endif