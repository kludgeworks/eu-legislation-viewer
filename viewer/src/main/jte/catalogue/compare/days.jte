@import dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.web.view.CompareView
@param CompareView compare
<%-- Shows the two days of a comparison, and the amending acts between them. CompareView supplies
     the data. The heading names the act, or the provision that the page is limited to. The day control
     (day.jte) has the two days, and keeps the provision. One line says the days in words, and how many
     amending acts changed the act or the provision between them. Each act is a link to its page.
     For one provision, a link opens the comparison of the whole act on the same two days. --%>
<section class="selection compare" aria-labelledby="compare-head">
  <h2 id="compare-head">${compare.heading()}</h2>
  @template.catalogue.day(day = compare.picker())
  <p><b>${compare.days()}</b>@if(compare.said() != null) ${compare.said()}@for(var each : compare.acts()) <a href="${each.href()}">${each.act()}</a>${each.after()}@endfor@endif</p>
  @if(compare.whole() != null)
    <p><a href="${compare.whole()}" data-view="changes">Compare the whole act on these two days</a></p>
  @endif
</section>
