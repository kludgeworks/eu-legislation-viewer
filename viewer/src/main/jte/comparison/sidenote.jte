@import dev.groknull.kludgeworks.eu.legislation.viewer.comparison.web.view.DifferencesView
@import dev.groknull.kludgeworks.eu.legislation.viewer.comparison.web.view.SidenotePieces
@import dev.groknull.kludgeworks.eu.legislation.viewer.comparison.web.view.SidenoteView
@param DifferencesView.Shown shown
@param SidenoteView sidenote = null
@param SidenotePieces pieces = null
@param boolean named = true
<%-- Shows what a difference's sidenote says. In the Changes and Whole text views, the sidenote is
     beside the difference. In the Synopsis view, it is in the last column. It shows the difference's
     number and its occurrence, what happened, which acts made it and when each applies. Where a change
     does not apply on the later day of the comparison yet, a line in amber says so. The Synopsis
     names the occurrence in its own column (named false). --%>
<b>${shown.number()}</b>@if(named && shown.named() != null) <span class="occurrence">${shown.named()}</span>@endif
<%-- The hover of who made it lists how the acts were published. If the view sends it once for all
     the sidenotes (pieces), js/sidenotes.js sets it from there. --%>
!{var published = pieces == null || sidenote == null ? null : pieces.published(sidenote);}
@if(sidenote != null && sidenote.made() != null)@if(published != null)<made-by class="made" data-published="${published}">${sidenote.made()}</made-by>@else<span class="made" title="${sidenote.published()}">${sidenote.made()}</span>@endif@endif
<%-- Shows what happened, unless the line about who made it already says it all. Example:
     "Inserted by 2019/1583". --%>
@if(sidenote == null || sidenote.made() == null)<span class="label">${shown.label()}</span>@endif
<%-- A change of the difference does not apply on the later day of the comparison yet: a
     prospective amendment. --%>
@if(sidenote != null && sidenote.prospective() != null)<span class="prospective">${sidenote.prospective()}</span>@endif
@if(sidenote != null)
  <%-- Shows one date line for each act, and each line starts with the act. Six or more lines go
     into a fold. If the view sends them once for all the sidenotes (pieces), js/sidenotes.js fills
     the fold from there when the reader opens it. --%>
  !{var dates = pieces == null ? null : pieces.dates(sidenote);}
  @if(dates != null)
    <shared-dates data-dates="${dates}"><details class="dates"><summary>${sidenote.dates().size()} dates · <span class="show">show</span></summary></details></shared-dates>
  @elseif(sidenote.dates().size() >= SidenotePieces.FOLDED)
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
