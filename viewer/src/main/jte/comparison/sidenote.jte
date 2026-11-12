@import dev.groknull.kludgeworks.eu.legislation.viewer.comparison.web.view.ChangesView
@import dev.groknull.kludgeworks.eu.legislation.viewer.comparison.web.view.SidenoteParts
@import dev.groknull.kludgeworks.eu.legislation.viewer.comparison.web.view.SidenoteView
@param ChangesView.Shown shown
@param SidenoteView sidenote = null
@param SidenoteParts parts = null
<%-- Shows what a difference's sidenote says. In the Changes and Whole text views, the sidenote is
     beside the difference. In the Synopsis view, it is in the last column. It shows the difference's
     number, what happened, which acts made it and when each applies. --%>
<b>${shown.number()}</b>
<%-- The hover of who made it lists how the acts were published. If the view sends it once for all
     the sidenotes (parts), js/sidenotes.js sets it from there. --%>
!{var published = parts == null || sidenote == null ? null : parts.published(sidenote);}
@if(sidenote != null && sidenote.made() != null)@if(published != null)<made-by class="made" data-published="${published}">${sidenote.made()}</made-by>@else<span class="made" title="${sidenote.published()}">${sidenote.made()}</span>@endif@endif
<%-- Shows what happened, unless the line about who made it already says it all. Example:
     "Inserted by 2019/1583". --%>
@if(sidenote == null || sidenote.made() == null)<span class="note">${shown.note()}</span>@endif
@if(sidenote != null)
  <%-- Shows one date line for each act, and each line starts with the act. Six or more lines go
     into a fold. If the view sends them once for all the sidenotes (parts), js/sidenotes.js fills
     the fold from there when the reader opens it. --%>
  !{var dates = parts == null ? null : parts.dates(sidenote);}
  @if(dates != null)
    <shared-dates data-dates="${dates}"><details class="dates"><summary>${sidenote.dates().size()} dates · <span class="show">show</span></summary></details></shared-dates>
  @elseif(sidenote.dates().size() >= SidenoteParts.FOLDED)
    <details class="dates"><summary>${sidenote.dates().size()} dates · <span class="show">show</span></summary>
      @for(var applies : sidenote.dates())
        <span class="applies">@template.comparison.applies(applies = applies)</span>
      @endfor
    </details>
  @else
    @for(var applies : sidenote.dates())
      <span class="applies">@template.comparison.applies(applies = applies)</span>
    @endfor
  @endif
@endif
