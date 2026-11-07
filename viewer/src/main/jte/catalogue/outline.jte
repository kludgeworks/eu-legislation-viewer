@import dev.groknull.kludgeworks.eu.legislation.viewer.content.Outline.Entry
@import java.util.List
@param List<Entry> entries
@param boolean open
<%-- Shows one level of the text's outline (Outline). Each unit links to its place in the text by
     its eId. A unit with units below it is a details element that lists them. The details elements
     are open at the outermost level. --%>
<ul>
  @for(var entry : entries)
    !{var label = (entry.num() + " " + entry.heading()).strip();}
    !{var href = entry.eId().isEmpty() ? null : "#" + entry.eId();}
    <li>
      @if(entry.entries().isEmpty())
        <a href="${href}">${label.isEmpty() ? entry.kind() : label}</a>
      @else
        <details open="${open}">
          <summary><a href="${href}">${label.isEmpty() ? entry.kind() : label}</a></summary>
          @template.catalogue.outline(entries = entry.entries(), open = false)
        </details>
      @endif
    </li>
  @endfor
</ul>
