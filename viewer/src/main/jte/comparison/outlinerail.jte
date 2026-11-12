@import dev.groknull.kludgeworks.eu.legislation.viewer.comparison.web.view.OutlineView
@param OutlineView outline
@param int total
<%-- Shows the rail that a comparison's outline collapses to. It shows only while the outline is
     collapsed. "Outline" is set vertically, then the number of differences, then one link for each
     part with differences, with its count (one for each section in a one-part comparison). Thus the
     rail still works as a map. js/outline.js marks the part in view. --%>
<div class="rail-map">
  <span class="rail-label">Outline</span>
  <span class="rail-total" title="${total == 1 ? "1 difference" : total + " differences"}">${total}</span>
  @for(var entry : outline.rail())
    <a href="${entry.href()}" data-first="${entry.first()}" title="${entry.title()}"><span>${entry.label()}</span><b>${entry.count()}</b></a>
  @endfor
</div>
