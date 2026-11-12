@import dev.groknull.kludgeworks.eu.legislation.viewer.comparison.web.view.ActsView
@param ActsView acts
<%-- Shows the acts between a comparison's two versions, as one chip for each act. A chip links to
     the comparison limited to that act's changes. The chip that is on links back to all acts.
     After the count, each chip also shows how many differences its act made. Until then, htmx loads
     the counts from acts.counting() and swaps this whole template for the counted one. If there are
     more than six chips, the chips of acts that made no differences here go into a fold. After the
     count, a dashed chip shows the differences that no single act is known to have made. --%>
<div class="between" id="acts" hx-get="${acts.counting()}" hx-trigger="${acts.counting() != null ? "load" : null}" hx-swap="outerHTML">
  <span class="card-kind">${acts.head()}</span>
  <div class="chips">
    @for(var chip : acts.chips())
      @template.comparison.chip(chip = chip)
    @endfor
    @if(acts.unknown() != null)<span class="chip unknown"><span class="label">act not known</span><span class="count">${acts.unknown()}</span></span>@endif
  </div>
  @if(!acts.none().isEmpty())
    <details class="more-acts">
      <summary>${acts.more()}</summary>
      <div class="chips">
        @for(var chip : acts.none())
          @template.comparison.chip(chip = chip)
        @endfor
      </div>
    </details>
  @endif
</div>
