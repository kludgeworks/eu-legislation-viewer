@import dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.web.view.PreparingView.Progress
@param Progress progress
<%-- Shows the progress of the act's preparation. PreparingView.Progress supplies the data. While
     nothing has started for the act, it shows that the act waits its turn. The queue is shared by
     all acts. After the start, it shows the three steps. The first converts each version and lists
     the versions that are being converted now, or that wait to be tried again after an attempt that
     failed. The second matches the provisions of each pair of neighbouring versions, as soon as
     both are ready. The third is done at once: Cellar gives when each amended provision applies
     with the act.
     Under that, it shows the time the preparation has taken and how many versions are ready. The
     page's clock (<elapsed-time>) keeps the time current. While the act waits, a light aside shows beside
     the plain line. <rotating-aside> changes the aside every few seconds. The stream swaps this element by
     its id (#progress). It is busy (aria-busy) while it changes; the line #told tells screen readers
     how many versions are ready. --%>
<div id="progress" class="progress" aria-busy="true">
  @if(progress.waiting() != null)
    <div class="waiting">
      <p><strong>Waiting its turn.</strong> ${progress.waiting()}</p>
      <rotating-aside class="aside" aria-hidden="true" asides="${progress.asides()}">${Progress.ASIDES.getFirst()}</rotating-aside>
    </div>
  @else
    <ol class="stages">
      <li>
        <strong>Convert each version</strong>
        @for(var line : progress.running())
          <p>${line}</p>
        @endfor
        <progress value="${progress.settled()}" max="${progress.size()}">${progress.counts()}</progress>
      </li>
      <li>
        <strong>Match provisions between versions</strong>
        <p>Each pair of neighbouring versions, as soon as both are ready</p>
      </li>
      <li class="done">
        <strong>Load when each amended provision applies</strong>
        <p>${progress.dates()}</p>
      </li>
    </ol>
  @endif
  <p class="foot">Elapsed <elapsed-time seconds="${progress.seconds()}"><time datetime="PT${progress.seconds()}S">${progress.elapsed()}</time></elapsed-time> · ${progress.counts()}</p>
</div>
