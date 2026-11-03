@import dev.groknull.kludgeworks.eu.legislation.viewer.content.Element
@import dev.groknull.kludgeworks.eu.legislation.viewer.content.web.LegalRenderer
@param Element node
@param LegalRenderer html
@param String separator
<%-- an entry of a definition list: its prefix, its term, the list's separator, its definition --%><div class="unit unit-${node.attribute("kind")}${"true".equals(node.attribute("numbered")) ? " numbered" : ""}" id="${node.attribute("eId")}" data-depth="${node.attribute("depth")}">${html.children(node, "provenance")}${html.children(node, "num")} ${html.children(node, "heading")} <span class="separator">${separator}</span> ${html.others(node, "provenance", "num", "heading")}</div>