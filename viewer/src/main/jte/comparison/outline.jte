@import dev.groknull.kludgeworks.eu.legislation.viewer.comparison.web.view.OutlineView.Branch
@import java.util.List
@param List<Branch> entries
<%-- Shows one level of the later text's outline in a comparison. OutlineView supplies the data.
     Each unit shows its count of differences and links to its first difference. A unit with units
     below it is a details element, open if the unit has differences. A bar shows the share of the
     differences of each unit that has any, down to the point. The differences in a unit's points are rows one level below it:
     a dot, the point's own num and the number of the difference. --%>
<ul>
  @for(var entry : entries)
    !{var has = entry.count() > 0 ? "has" : null;}
    !{var count = entry.count() > 0 ? String.valueOf(entry.count()) : "";}
    <li>
      @if(entry.entries().isEmpty())
        <a href="${entry.href()}" class="${has}"><span>${entry.label()}</span>@if(entry.count() > 0)<span class="bar"><i style="${entry.barStyle()}"></i></span>@else<span></span>@endif<span class="n">${count}</span></a>
        @template.comparison.outlinerows(entry = entry)
      @else
        <details open="${entry.count() > 0}">
          <summary><a href="${entry.href()}" class="${has}"><span>${entry.label()}</span>@if(entry.count() > 0)<span class="bar"><i style="${entry.barStyle()}"></i></span>@else<span></span>@endif<span class="n">${count}</span></a></summary>
          @template.comparison.outlinerows(entry = entry)
          @template.comparison.outline(entries = entry.entries())
        </details>
      @endif
    </li>
  @endfor
</ul>
