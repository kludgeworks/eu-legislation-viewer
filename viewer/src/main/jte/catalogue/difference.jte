@import dev.groknull.kludgeworks.eu.legislation.viewer.comparison.web.view.ChangesView
@import dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.web.view.SidenoteView
@param ChangesView.Shown shown
@param SidenoteView sidenote = null
@param boolean greyed = false
@param gg.jte.Content unit
<%-- A provision that differs, as the Changes and Whole text views show it: numbered, its unit as it
     reads, marked (unit), with a sidenote beside it: what happened, which acts made it and when each
     applies, what to know of who made it, and what it was before. Greyed where it may be the
     filter's act's change, not known --%>
<section class="difference difference-${shown.kind()}${greyed ? " unknown" : ""}" id="difference-${shown.number()}">
  <sup class="difference-number">${shown.number()}</sup>
  <aside class="sidenote">
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
  </aside>
  ${unit}
</section>
