@import dev.groknull.kludgeworks.eu.legislation.viewer.comparison.web.view.ChangesView
@import dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.web.view.SidenoteView
@param ChangesView.Shown shown
@param SidenoteView sidenote = null
<%-- What a difference's sidenote says, beside it in the natural views, in the Synopsis's last
     column: its number, what happened, which acts made it and when each applies, what to know of
     who made it, and what it was before --%>
<b>${shown.number()}</b>
@if(sidenote != null && sidenote.made() != null)<span class="made" title="${sidenote.published()}">${sidenote.made()}</span>@endif
<%-- what happened, unless who made it says it all: Inserted by 2019/1583 --%>
@if(sidenote == null || sidenote.made() == null || shown.kind().equals("changed"))<span class="note">${shown.note()}</span>@endif
@if(sidenote != null)
  <%-- a date line per act, each starting with it; six or more folded (Design D-43) --%>
  @if(sidenote.dates().size() >= 6)
    <details class="dates"><summary>${sidenote.dates().size()} acts’ dates</summary>
      @for(var applies : sidenote.dates())
        <span class="applies">@template.catalogue.applies(applies = applies)</span>
      @endfor
    </details>
  @else
    @for(var applies : sidenote.dates())
      <span class="applies">@template.catalogue.applies(applies = applies)</span>
    @endfor
  @endif
  @if(sidenote.why() != null)<span class="why">${sidenote.why()}</span>@endif
  @if(sidenote.before() != null)<span class="before">${sidenote.before()}</span>@endif
@endif
