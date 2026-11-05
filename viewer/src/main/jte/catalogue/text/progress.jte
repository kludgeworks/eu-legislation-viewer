@import dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.Preparation
@param Preparation preparation
<%-- How the act's preparation stands (Preparation): waiting its turn while none of its versions is
     being built (the queue is every act's), else how many are ready --%>
<p class="status">
  @if(preparation.running() == 0)
    Waiting its turn: other acts are being prepared first.
  @else
    Preparing this act: ${preparation.ready()} of ${preparation.size()} versions ready. It opens
    here as soon as every version is.
  @endif
</p>
