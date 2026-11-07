@import dev.groknull.kludgeworks.eu.legislation.viewer.comparison.web.view.ChangesView
@import dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.web.view.CompareView
@import java.util.List
@param ChangesView changes
@param List<CompareView.ViewLink> views
@param CompareView.Only only = null
@param boolean corrected = false
<%-- A comparison's controls (Design: The act page, div.controls): its views, a link each (Changes,
     Whole text, Synopsis), the one shown current; how many differences and of what kind; limited to
     one act's changes (only), a banner saying so, linking to all acts'; at the end, what the marks on
     the page are (Design D-32): a correction's only where corrigenda made a difference --%>
<div class="controls">
  <nav class="views" aria-label="View">
    @for(var view : views)
      <a class="view" href="${view.href()}" aria-current="${view.on() ? "page" : null}">${view.label()}</a>
    @endfor
  </nav>
  <b>${changes.count()}</b>
  @if(changes.breakdown() != null)<span>${changes.breakdown()}</span>@endif
  @if(!changes.numbers().isEmpty())<span class="legend" aria-label="Marks"><ins class="diff">inserted</ins> <del class="diff">deleted</del>@if(corrected) <span class="corrected"><ins class="diff">corrected in</ins> <del class="diff">corrected out</del></span>@endif</span>@endif
  @if(only != null)<span class="only" role="status">Only ${only.act()}’s changes · <a href="${only.all()}">show all acts</a></span>@endif
</div>
