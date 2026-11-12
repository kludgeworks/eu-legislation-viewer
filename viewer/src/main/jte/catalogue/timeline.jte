@import dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.web.view.TimelineView
@import dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.web.view.TimelineView.Tick
@import dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.web.view.TimelineView.Merged
@param TimelineView timeline
<%-- Shows the act's versions on a timeline to scale. TimelineView supplies the data. Each mark is a
     link. In Read mode, it links to its version's page. In Compare mode, it links to the comparison
     with the nearer end moved to that version.
     Versions that are too close to click one by one share one mark. That mark is a details element
     that opens a list of them. The details elements share one name, so only one opens at a time.
     The compared range is a band.
     While the act is being prepared, each mark shows the state of its version's text and is not a
     link. The stream swaps this element by its id (#timeline). --%>
<section id="timeline" class="timeline" aria-label="Versions">
  <p class="timeline-head"><span>Versions</span></p>
  <div class="axis">
    <span class="axis-line"></span>
    @if(timeline.band() != null)
      <span class="band" style="${timeline.band().style()}"></span>
    @endif
    @for(var year : timeline.years())
      <time class="year" datetime="${year.year()}" style="${year.style()}">${year.year()}</time>
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
