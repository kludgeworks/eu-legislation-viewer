@import dev.groknull.kludgeworks.eu.legislation.viewer.comparepage.ComparePage
@import dev.groknull.kludgeworks.eu.legislation.viewer.content.Outline.Entry
@import java.util.List
@param List<Entry> entries
@param ComparePage page
<%-- Shows the outline of an annex inserted whole: its units, without their text, each linking to
     its place in Read in the version at the To end. A unit with no headed units in it says so. --%>
@if(entries.isEmpty())
  <p class="status">It has no parts with headings of their own.</p>
@else
<ul class="annex-outline">
  @for(var entry : entries)
    !{var label = (entry.num() + " " + entry.heading()).strip();}
    <li>@if(entry.eId().isEmpty())${label.isEmpty() ? entry.kind() : label}@else<a href="${page.read(entry.eId())}">${label.isEmpty() ? entry.kind() : label}</a>@endif
      @if(!entry.entries().isEmpty())@template.comparison.annexoutline(entries = entry.entries(), page = page)@endif
    </li>
  @endfor
</ul>
@endif
