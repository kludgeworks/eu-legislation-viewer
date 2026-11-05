@import dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.Preparation
@param Preparation preparation
@param String self
<%-- The act being prepared in the language, every version of it, before any of its pages shows a
     text: waiting its turn while none of its versions is being built (the queue is every act's),
     else how many are ready; polling this URL until all are settled --%>
<div id="text" hx-get="${self}" hx-trigger="load delay:1s" hx-swap="outerHTML">
  <p class="status">
    @if(preparation.running() == 0)
      Waiting its turn: other acts are being prepared first.
    @else
      Preparing this act: ${preparation.ready()} of ${preparation.size()} versions ready. It opens
      here as soon as every version is.
    @endif
  </p>
</div>
