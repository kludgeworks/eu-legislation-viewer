@import dev.groknull.kludgeworks.eu.legislation.viewer.comparison.web.view.ChangesView
@import dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.web.view.SidenoteView
@param ChangesView.Shown shown
@param SidenoteView sidenote = null
<%-- What a difference's sidenote says, beside it in the natural views, in the Synopsis's last
     column: its number, what happened, which acts made it and when each applies, what to know of
     who made it, and what it was before --%>
<b>${shown.number()}</b>
@if(sidenote != null && sidenote.made() != null)<span class="made">${sidenote.made()}</span>@endif
<%-- what happened, unless who made it says it all: Inserted by 2019/1583 --%>
@if(sidenote == null || sidenote.made() == null || shown.kind().equals("changed"))<span class="note">${shown.note()}</span>@endif
@if(sidenote != null)
  @for(var applies : sidenote.dates())
    <span class="applies">${applies.when()}@if(applies.clause() != null)${applies.between()}<a href="${applies.clause().href()}" rel="external">${applies.clause().label()}</a>@for(var each : applies.amendedBy()), as amended by <a href="${each.href()}" rel="external">${each.label()}</a>@endfor${applies.after()}@endif</span>
  @endfor
  @if(sidenote.why() != null)<span class="why">${sidenote.why()}</span>@endif
  @if(sidenote.before() != null)<span class="before">${sidenote.before()}</span>@endif
@endif
