@import dev.groknull.kludgeworks.eu.legislation.viewer.content.Outline.Entry
@import java.util.List
@param List<Entry> entries
@param boolean open
<%-- Shows one level of the text's outline (Outline). Each occurrence links to its place in the text by
     its eId. An occurrence with occurrences below it has a details element after its link that lists them. Its
     summary is only the fold's button, because a link in a summary is not reliable for the keyboard
     and for screen readers. The details elements are open at the outermost level. --%>
<ul>
  @for(var entry : entries)
    !{var label = (entry.num() + " " + entry.heading()).strip();}
    !{var name = label.isEmpty() ? entry.kind() : label;}
    !{var href = entry.eId().isEmpty() ? null : "#" + entry.eId();}
    <li class="${entry.entries().isEmpty() ? null : "branch"}">
      <a href="${href}">${name}</a>
      @if(!entry.entries().isEmpty())
        <details open="${open}">
          <summary><span class="visually-hidden">The parts of ${name}</span></summary>
          @template.catalogue.outline(entries = entry.entries(), open = false)
        </details>
      @endif
    </li>
  @endfor
</ul>
