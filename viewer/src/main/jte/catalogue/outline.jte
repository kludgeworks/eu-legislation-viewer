@import dev.groknull.kludgeworks.eu.legislation.viewer.content.Outline.Entry
@import java.util.List
@param List<Entry> entries
@param boolean open
<%-- A level of the text's outline (Outline): each unit a link to it in the text, by its eId, a unit
     with units below it a details listing them, open at the outermost level --%>
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
