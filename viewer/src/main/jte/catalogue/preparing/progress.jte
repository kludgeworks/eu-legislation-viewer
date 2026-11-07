@import dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.web.view.PreparingView.Progress
@param Progress progress
<%-- Shows the progress of the act's preparation. PreparingView.Progress supplies the data. While
     nothing has started for the act, it shows that the act waits its turn. The queue is shared by
     all acts. After the start, it shows the one step, which converts each version. It lists the
     versions that are being converted now.
     Under that, it shows the time the preparation has taken and how many versions are ready. The
     page's clock (clock.js) keeps the time current. While the act waits, a light aside shows beside
     the plain line. clock.js changes the aside every few seconds. The stream swaps this element by
     its id (#progress). --%>
<div id="progress" class="progress">
  @if(progress.waiting() != null)
    <div class="waiting">
      <p><strong>Waiting its turn.</strong> ${progress.waiting()}</p>
      <p class="aside" aria-hidden="true" data-asides="${progress.asides()}">${Progress.ASIDES.getFirst()}</p>
    </div>
  @else
    <ol class="stages">
      <li>
        <strong>Convert each version</strong>
        @for(var line : progress.running())
          <p>${line} · converting, then storing</p>
        @endfor
        <progress value="${progress.settled()}" max="${progress.size()}">${progress.counts()}</progress>
      </li>
    </ol>
  @endif
  <p class="foot">Elapsed <time data-elapsed="${progress.seconds()}">${progress.elapsed()}</time> · ${progress.counts()}</p>
</div>
