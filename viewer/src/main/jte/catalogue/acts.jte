@import dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.web.view.CompareView
@param CompareView.Acts acts
<%-- The acts between a comparison's two versions (Design: Acts between the two versions): a chip
     each, linking to the comparison limited to its changes (on: to all acts again), with how many
     differences it made once counted; until then, counted from beside the page, swapped for this
     counted. More than six, those that made none here fold --%>
<div class="between" id="acts" hx-get="${acts.counting()}" hx-trigger="${acts.counting() != null ? "load" : null}" hx-swap="outerHTML">
  <span class="card-kind">${acts.head()}</span>
  <div class="chips">
    @for(var chip : acts.chips())
      @template.catalogue.chip(chip = chip)
    @endfor
  </div>
  @if(!acts.none().isEmpty())
    <details class="more-acts">
      <summary>${acts.more()}</summary>
      <div class="chips">
        @for(var chip : acts.none())
          @template.catalogue.chip(chip = chip)
        @endfor
      </div>
    </details>
  @endif
</div>
