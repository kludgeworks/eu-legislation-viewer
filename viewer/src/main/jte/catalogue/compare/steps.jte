@import dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.web.view.CompareView
@param CompareView compare
<%-- Shows the steps between the two days of the comparison of one provision: the changes that the
     amending acts made to it, oldest first. One line is for one change, as in the history of the
     provision on Read (provision.jte): the act as a link to its page, what it did, the state of the
     change on the To day with the date and the clause, and a link to the change on the page of the
     act. A change that does not apply on the To day has an amber bar. --%>
<section class="compare-steps" aria-labelledby="steps-head">
  <h2 id="steps-head">The steps between these days</h2>
  <ol>
    @for(var line : compare.steps())
      <li class="${line.tone()}"><b>@if(line.href() != null)<a href="${line.href()}">${line.act()}</a>@else${line.act()}@endif</b> ${line.words()}@if(line.note() != null) ${line.note()}@endif@if(line.change() != null) <a href="${line.change()}">See this change</a>@endif</li>
    @endfor
  </ol>
</section>
