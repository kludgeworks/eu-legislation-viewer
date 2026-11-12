@import dev.groknull.kludgeworks.eu.legislation.viewer.comparison.web.view.ChangesView
@import dev.groknull.kludgeworks.eu.legislation.viewer.comparison.web.view.ControlsView
@import java.util.List
@param ChangesView changes
@param List<ControlsView.ViewLink> views
@param ControlsView.Only only = null
@param ControlsView.Legend legend
<%-- Shows a comparison's controls. There is one link for each view: Changes, Whole text and
     Synopsis. The view that shows is marked current. Then the controls say how many provisions
     differ. If the view is limited to one act's changes (only), a banner says so
     and links to the changes of all acts. If one act made every difference, the summary names it.
     At the end, a legend says what the marks on the page mean: it lists only the marks that the
     page shows. --%>
<div class="controls">
  <nav class="views" aria-label="View">
    @for(var view : views)
      <a class="view" href="${view.href()}" data-view="${view.code()}" aria-current="${view.on() ? "page" : null}">${view.label()}</a>
    @endfor
  </nav>
  <b>${changes.count()}</b>
  @if(legend.by() != null)<span class="by">by ${legend.by()}</span>@endif
  @if(legend.inserted() || legend.deleted() || legend.corrected())<span class="legend" aria-label="Marks">@if(legend.inserted())<ins class="diff">inserted</ins>@endif @if(legend.deleted())<del class="diff">deleted</del>@endif @if(legend.corrected())<span class="corrected"><ins class="diff">corrigendum</ins></span>@endif</span>@endif
  @if(only != null)<span class="only" role="status">Only ${only.act()}’s changes · <a href="${only.all()}">show all acts</a></span>@endif
</div>
