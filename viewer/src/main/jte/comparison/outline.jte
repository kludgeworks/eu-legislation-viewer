@import dev.groknull.kludgeworks.eu.legislation.viewer.comparison.web.view.OutlineView.Branch
@import java.util.List
@param List<Branch> entries
<%-- Shows one level of the later text's outline in a comparison. OutlineView supplies the data.
     Each occurrence shows its count of differences and links to its first difference. An occurrence with occurrences
     below it is a details element, open if the occurrence has differences. A bar shows the share of the
     differences of each occurrence that has any, down to the point. The differences in an occurrence's points are rows one level below it:
     a dot, the point's own num and the number of the difference. --%>
<ul>
  @for(var entry : entries)
    !{var has = entry.count() > 0 ? "has" : null;}
    !{var count = entry.count() > 0 ? String.valueOf(entry.count()) : "";}
    <li class="${entry.entries().isEmpty() ? null : "branch"}">
      <a href="${entry.href()}" class="${has}"><span>${entry.label()}</span>@if(entry.count() > 0)<span class="bar"><i style="${entry.barStyle()}"></i></span>@else<span></span>@endif<span class="n">${count}</span></a>
      @if(entry.entries().isEmpty())
        @template.comparison.outlinerows(entry = entry)
      @else
        <%-- Its summary is only the fold's button: a link in a summary is not reliable for the
             keyboard and for screen readers. --%>
        <details open="${entry.count() > 0}">
          <summary><span class="visually-hidden">The parts of ${entry.label()}</span></summary>
          @template.comparison.outlinerows(entry = entry)
          @template.comparison.outline(entries = entry.entries())
        </details>
      @endif
    </li>
  @endfor
</ul>
