@import dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.web.view.TimelineView
@import dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.web.view.TimelineView.Tick
@import dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.web.view.TimelineView.Merged
@param TimelineView timeline
<%-- The act's versions to scale (TimelineView): each mark a link (to its version's page, reading;
     to the comparison with the nearer end moved to it, comparing), versions too close to click apart
     one mark that opens a list of them (details, which their shared name keeps to one open at a
     time), lags hatched, the range compared a band. While the act is prepared, the marks are its
     versions' texts as they stand (state), links to nothing; the stream swaps it (#timeline) --%>
<section id="timeline" class="timeline" aria-label="Versions">
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
        @if(tick.href() != null)
          <a class="tick ${tick.state()}" style="${tick.style()}" href="${tick.href()}" title="${tick.label()}" aria-label="${tick.label()}" aria-current="${tick.selected() ? timeline.current() : null}"></a>
        @else
          <span class="tick ${tick.state()}" style="${tick.style()}" title="${tick.label()}"></span>
        @endif
      @elseif(mark instanceof Merged merged)
        <details name="merged" class="merged${merged.selected() ? " selected" : ""}${merged.opensLeft() ? " left" : ""}${merged.state() != null ? " " + merged.state() : ""}" style="${merged.style()}">
          <summary title="${merged.label()}" aria-label="${merged.label()}">${merged.versions().size()}</summary>
          <div class="merged-list">
            <span>${merged.label()}</span>
            @for(var version : merged.versions())
              @if(version.href() != null)
                <a href="${version.href()}" aria-current="${version.selected() ? timeline.current() : null}">${version.label()}</a>
              @else
                <span class="${version.state()}">${version.label()}</span>
              @endif
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
      @if(step.href() != null)<a href="${step.href()}">${step.label()}</a>@else<span aria-disabled="true" title="${step.nowhere()}">${step.label()}</span>@endif
    @endfor
  </nav>
</section>
