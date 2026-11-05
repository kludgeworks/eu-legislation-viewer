@import dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.web.view.PreparingView.Progress
@param Progress progress
<%-- How the act's preparation stands (PreparingView.Progress): waiting its turn while nothing has
     started for it (the queue is every act's), else its one step, converting each version, with the
     versions being converted now; then the time it has taken, which the page's clock keeps
     (clock.js), and how many versions are ready. The stream swaps it (#progress) --%>
<div id="progress" class="progress">
  @if(progress.waiting() != null)
    <p class="waiting"><strong>Waiting its turn.</strong> ${progress.waiting()}</p>
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
