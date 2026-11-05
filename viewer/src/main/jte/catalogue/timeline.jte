@import dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.web.view.TimelineView
@import dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.web.view.TimelineView.Tick
@import dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.web.view.TimelineView.Merged
@param TimelineView timeline
<%-- The act's versions to scale (TimelineView): each mark a link (to its version's page, reading;
     to the comparison with the nearer end moved to it, comparing), versions too close to click apart
     one mark that opens a list of them (details, which their shared name keeps to one open at a
     time), lags hatched, the range compared a band --%>
<section class="timeline" aria-label="Versions">
  <p class="timeline-head"><span>Versions</span><span>${timeline.hint()}</span></p>
  <div class="axis">
    <span class="axis-line"></span>
    @if(timeline.band() != null)
      <span class="band" style="${timeline.band().style()}"></span>
    @endif
    @for(var year : timeline.years())
      <span class="year" style="${year.style()}">${year.year()}</span>
    @endfor
    @for(var lag : timeline.lags())
      <span class="lag" style="${lag.style()}" title="${lag.text()}"></span>
    @endfor
    @for(var mark : timeline.marks())
      @if(mark instanceof Tick tick)
        <a class="tick" style="${tick.style()}" href="${tick.href()}" title="${tick.label()}" aria-label="${tick.label()}" aria-current="${tick.selected() ? timeline.current() : null}"></a>
      @elseif(mark instanceof Merged merged)
        <details name="merged" class="merged${merged.selected() ? " selected" : ""}${merged.opensLeft() ? " left" : ""}" style="${merged.style()}">
          <summary title="${merged.label()}" aria-label="${merged.label()}">${merged.versions().size()}</summary>
          <div class="merged-list">
            <span>${merged.label()}</span>
            @for(var version : merged.versions())
              <a href="${version.href()}" aria-current="${version.selected() ? timeline.current() : null}">${version.label()}</a>
            @endfor
          </div>
        </details>
      @endif
    @endfor
    @for(var tag : timeline.tags())
      <span class="tag" style="${tag.style()}">${tag.label()}</span>
    @endfor
  </div>
  <nav class="steps" aria-label="Versions either side">
    @for(var step : timeline.steps())
      @if(step.href() != null)<a href="${step.href()}">${step.label()}</a>@else<span>${step.label()}</span>@endif
    @endfor
  </nav>
</section>
