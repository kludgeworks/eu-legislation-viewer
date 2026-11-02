@import dev.groknull.kludgeworks.eu.legislation.viewer.ingest.IngestStatus
@param IngestStatus status
@param String self
<div id="text" hx-get="${self}" hx-trigger="load delay:1s" hx-swap="outerHTML">
  <p class="status">
    @if(status.state() == IngestStatus.State.QUEUED)
      Waiting its turn to be made from Cellar's Formex…
    @elseif(status.stage() == IngestStatus.Stage.STORING)
      Storing the text…
    @else
      Converting Cellar's Formex…
    @endif
  </p>
</div>
