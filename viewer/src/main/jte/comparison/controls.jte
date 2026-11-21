@import dev.groknull.kludgeworks.eu.legislation.viewer.comparison.web.view.ChangesView
@import dev.groknull.kludgeworks.eu.legislation.viewer.comparison.web.view.ControlsView
@import java.util.List
@param ChangesView changes
@param List<ControlsView.ViewLink> views
@param ControlsView.Legend legend
<%-- Shows a comparison's controls. There is one link for each view: Changes, Whole text and
     Synopsis. The view that shows is marked current. Then the controls say how many provisions
     differ. If one act made every difference, the summary names it.
     At the end, a legend says what the edits on the page mean: it lists only the kinds that the
     page shows. --%>
<div class="controls">
  <nav class="views" aria-label="View">
    @for(var view : views)
      <a class="view" href="${view.href()}" data-view="${view.code()}" aria-current="${view.on() ? "page" : null}">${view.label()}</a>
    @endfor
  </nav>
  <b>${changes.count()}</b>
  @if(legend.by() != null)<span class="by">by ${legend.by()}</span>@endif
  @if(legend.inserted() || legend.deleted() || legend.corrected())<span class="legend" aria-label="Legend">@if(legend.inserted())<ins class="diff">inserted</ins>@endif @if(legend.deleted())<del class="diff">deleted</del>@endif @if(legend.corrected())<span class="corrected"><ins class="diff">corrigendum</ins></span>@endif</span>@endif
</div>
