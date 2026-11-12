@import dev.groknull.kludgeworks.eu.legislation.viewer.comparison.web.view.OutlineView.Branch
@param Branch entry
<%-- Shows the differences in a unit's points as rows of the outline, one level below the unit:
     a dot, the point's own num, and the number of the difference in grey. Each row jumps to its
     difference. --%>
@if(!entry.rows().isEmpty())
  <ul class="rows">
    @for(var row : entry.rows())
      <li><a href="#difference-${row.number()}" aria-label="Difference ${row.number()}, point ${row.label()}"><span>${row.label()}</span><span></span><span class="n">${row.number()}</span></a></li>
    @endfor
  </ul>
@endif
