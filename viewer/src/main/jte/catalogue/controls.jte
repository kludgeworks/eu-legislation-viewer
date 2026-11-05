@import dev.groknull.kludgeworks.eu.legislation.viewer.comparison.web.view.ChangesView
@import dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.web.view.CompareView
@import java.util.List
@param ChangesView changes
@param List<CompareView.ViewLink> views
@param CompareView.Only only = null
<%-- A comparison's controls (Design: The act page, div.controls): its views, a link each (Changes,
     Whole text, Synopsis), the one shown current; how many differences and of what kind; limited to
     one act's changes (only), a banner saying so, linking to all acts' --%>
<div class="controls">
  <nav class="views" aria-label="View">
    @for(var view : views)
      @if(view.href() == null)<span class="view" aria-disabled="true" title="Not built yet">${view.label()}</span>
      @else<a class="view" href="${view.href()}" aria-current="${view.on() ? "page" : null}">${view.label()}</a>@endif
    @endfor
  </nav>
  <b>${changes.count()}</b>
  @if(changes.breakdown() != null)<span>${changes.breakdown()}</span>@endif
  @if(only != null)<span class="only" role="status">Only ${only.act()}’s changes · <a href="${only.all()}">show all acts</a></span>@endif
</div>
