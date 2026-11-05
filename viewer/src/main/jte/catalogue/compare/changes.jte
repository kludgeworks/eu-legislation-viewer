@import dev.groknull.kludgeworks.eu.legislation.viewer.comparison.Comparison
@import dev.groknull.kludgeworks.eu.legislation.viewer.comparison.Difference
@param Comparison comparison
<%-- The comparison worked out: how many differences, of what kind, and which provisions (by eId),
     until the Changes view sets them out (Design: Mode: Compare) --%>
!{var differences = comparison.differences();}
!{var inserted = differences.stream().filter(d -> d.kind() == Difference.Kind.INSERTED).count();}
!{var deleted = differences.stream().filter(d -> d.kind() == Difference.Kind.DELETED).count();}
<div id="text">
  <div class="controls">
    @if(differences.isEmpty())
      <b>No differences</b>
    @else
      <b>${differences.size()} ${differences.size() == 1 ? "difference" : "differences"}</b>
      <span>${inserted} inserted · ${deleted} deleted · ${differences.size() - inserted - deleted} changed</span>
    @endif
  </div>
  <ol class="differences mono">
    @for(var difference : differences)
      <li>${difference.eId()} <span>${difference.kind().name().toLowerCase(java.util.Locale.ROOT)}</span></li>
    @endfor
  </ol>
</div>
