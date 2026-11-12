@import dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.web.view.TimelineView
@param TimelineView timeline
<%-- Shows the act's versions on a book ruler. TimelineView supplies the data and every position.
     One hairline, the years above it like running heads, each year's start a short tick. A run of
     years with no version is one break (⫽). Each version is a dot, and the dot is a link: in Read to
     the law on its first day, in Compare to the comparison with the nearer end moved to it. Hover or
     focus enlarges a dot and says its date, the acts that made it and its number. The arrow keys
     move between the dots (js/timeline.js). Adoption is an open dot, today a dashed line, a version
     after today a dashed dot. The days the reader chose are green balls, with their kicker and date
     below; in Compare the range between them is green.
     While the act is being prepared, each dot shows the state of its version's text and is not a
     link. The stream swaps this element by its id (#timeline). --%>
<section id="timeline" class="timeline" aria-label="Versions">
  <p class="timeline-head"><span>Versions</span></p>
  <div class="ruler">
    <span class="hairline"></span>
    @if(timeline.band() != null)
      <span class="band" style="${timeline.band().style()}"></span>
    @endif
    @for(var year : timeline.years())
      <time class="year" datetime="${year.year()}" style="${year.style()}">${year.label()}</time>
    @endfor
    @for(var gap : timeline.breaks())
      <span class="break" style="${gap.style()}" title="${gap.label()}" aria-label="${gap.label()}">⫽</span>
    @endfor
    @if(timeline.adopted() != null)
      <span class="dot adopted" style="${timeline.adopted().style()}" data-label="${timeline.adopted().label()}" title="${timeline.adopted().label()}"></span>
    @endif
    <span class="today" style="${timeline.today().style()}" title="${timeline.today().label()}"></span>
    @for(var dot : timeline.dots())
      !{var classes = "dot" + (dot.future() ? " future" : "") + (dot.state() != null ? " " + dot.state() : "");}
      @if(dot.href() != null)
        <a class="${classes}" style="${dot.style()}" href="${dot.href()}" aria-label="${dot.label()}" data-label="${dot.label()}" aria-current="${dot.selected() ? timeline.current() : null}"></a>
      @else
        <span class="${classes}" style="${dot.style()}" data-label="${dot.label()}" title="${dot.label()}"></span>
      @endif
    @endfor
    @for(var ball : timeline.balls())
      <span class="ball" style="${ball.style()}" aria-hidden="true"></span>
      <span class="ball-label${ball.end() ? " end" : ""}${ball.stacked() ? " stacked" : ""}" style="${ball.style()}"><b>${ball.kicker()}</b> ${ball.day()}</span>
    @endfor
  </div>
  <nav class="steps" aria-label="Versions either side">
    @for(var step : timeline.steps())
      @if(step.href() != null)<a href="${step.href()}">${step.label()}</a>@else<span aria-disabled="true" title="${step.nowhere()}">${step.label()}</span>@endif
    @endfor
  </nav>
</section>
