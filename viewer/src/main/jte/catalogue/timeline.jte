@import dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.web.view.TimelineView
@import dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.web.view.TimelineView.Tick
@import dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.web.view.TimelineView.Merged
@param TimelineView timeline
<%-- The act's versions to scale (TimelineView): each mark a link to its version's page, versions
     too close to click apart one mark that opens a list of them (details, which their shared name
     keeps to one open at a time), lags hatched --%>
<section class="timeline" aria-label="Versions">
  <p class="timeline-head"><span>Versions</span><span>Click a version to read it</span></p>
  <div class="axis">
    <span class="axis-line"></span>
    @for(var year : timeline.years())
      <span class="year" style="${year.style()}">${year.year()}</span>
    @endfor
    @for(var lag : timeline.lags())
      <span class="lag" style="${lag.style()}" title="${lag.text()}"></span>
    @endfor
    @for(var mark : timeline.marks())
      @if(mark instanceof Tick tick)
        <a class="tick" style="${tick.style()}" href="${tick.href()}" title="${tick.label()}" aria-label="${tick.label()}" aria-current="${tick.reading() ? "page" : null}"></a>
      @elseif(mark instanceof Merged merged)
        <details name="merged" class="merged${merged.reading() ? " reading" : ""}${merged.opensLeft() ? " left" : ""}" style="${merged.style()}">
          <summary title="${merged.label()}" aria-label="${merged.label()}">${merged.versions().size()}</summary>
          <div class="merged-list">
            <span>${merged.label()}</span>
            @for(var version : merged.versions())
              <a href="${version.href()}" aria-current="${version.reading() ? "page" : null}">${version.label()}</a>
            @endfor
          </div>
        </details>
      @endif
    @endfor
    <span class="tag" style="${timeline.readingStyle()}">READING</span>
  </div>
  <nav class="steps" aria-label="Versions either side">
    @if(timeline.before() != null)<a href="${timeline.before()}">‹ version before</a>@else<span>‹ version before</span>@endif
    @if(timeline.after() != null)<a href="${timeline.after()}">version after ›</a>@else<span>version after ›</span>@endif
  </nav>
</section>
