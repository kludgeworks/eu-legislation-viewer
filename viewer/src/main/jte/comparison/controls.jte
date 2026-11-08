@import dev.groknull.kludgeworks.eu.legislation.viewer.comparison.web.view.ChangesView
@import dev.groknull.kludgeworks.eu.legislation.viewer.comparison.web.view.ControlsView
@import java.util.List
@param ChangesView changes
@param List<ControlsView.ViewLink> views
@param ControlsView.Only only = null
@param boolean corrected = false
<%-- Shows a comparison's controls. There is one link for each view: Changes, Whole text and
     Synopsis. The view that shows is marked current. Then the controls show how many differences
     there are and of what kind. If the view is limited to one act's changes (only), a banner says so
     and links to the changes of all acts. At the end, a legend says what the marks on the page mean.
     The legend shows the correction marks only where corrigenda made a difference. --%>
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
