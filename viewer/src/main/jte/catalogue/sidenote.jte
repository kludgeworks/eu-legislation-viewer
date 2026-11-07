@import dev.groknull.kludgeworks.eu.legislation.viewer.comparison.web.view.ChangesView
@import dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.web.view.SidenoteView
@param ChangesView.Shown shown
@param SidenoteView sidenote = null
<%-- Shows what a difference's sidenote says. In the Changes and Whole text views, the sidenote is
     beside the difference. In the Synopsis view, it is in the last column. It shows the difference's
     number, what happened, which acts made it and when each applies. It also says what to know about
     who made it, and what the text was before. --%>
<b>${shown.number()}</b>
@if(sidenote != null && sidenote.made() != null)<span class="made" title="${sidenote.published()}">${sidenote.made()}</span>@endif
<%-- Shows what happened, unless the line about who made it already says it all. Example:
     "Inserted by 2019/1583". --%>
@if(sidenote == null || sidenote.made() == null || shown.kind().equals("changed"))<span class="note">${shown.note()}</span>@endif
@if(sidenote != null)
  <%-- Shows one date line for each act, and each line starts with the act. Six or more lines go
     into a fold. --%>
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
