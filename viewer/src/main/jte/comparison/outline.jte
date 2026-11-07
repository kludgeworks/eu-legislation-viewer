@import dev.groknull.kludgeworks.eu.legislation.viewer.comparison.web.view.OutlineView.Branch
@import java.util.List
@param List<Branch> entries
<%-- Shows one level of the later text's outline in a comparison. OutlineView supplies the data.
     Each unit shows its count of differences and links to its first difference. A unit with units
     below it is a details element. If that unit has differences, the details element is open and a
     bar shows its share of them. The differences in a unit's points show as chips. --%>
<ul>
  @for(var entry : entries)
    !{var has = entry.count() > 0 ? "has" : null;}
    !{var count = entry.count() > 0 ? String.valueOf(entry.count()) : "";}
    <li>
      @if(entry.entries().isEmpty())
        <a href="${entry.href()}" class="${has}"><span>${entry.label()}</span><span></span><span class="n">${count}</span></a>
      @else
        <details open="${entry.count() > 0}">
          <summary><a href="${entry.href()}" class="${has}"><span>${entry.label()}</span>@if(entry.count() > 0)<span class="bar"><i style="${entry.barStyle()}"></i></span>@else<span></span>@endif<span class="n">${count}</span></a></summary>
          @template.comparison.outline(entries = entry.entries())
        </details>
      @endif
      @if(!entry.chips().isEmpty())
        <div class="chips">@for(var chip : entry.chips())<a href="#difference-${chip.number()}" aria-label="Difference ${chip.number()}, point ${chip.label()}">${chip.label()}</a>@endfor</div>
      @endif
    </li>
  @endfor
</ul>
