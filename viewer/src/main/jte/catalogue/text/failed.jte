@import dev.groknull.kludgeworks.eu.legislation.viewer.ingest.Failure
@param Failure failure
@param String eurLex
<div id="text">
  <p class="status">
    @if(failure == Failure.NO_FORMEX)
      Cellar has no Formex of this text, which is what the viewer reads.
    @elseif(failure == Failure.OUT_OF_SCOPE)
      This document isn't a legal act the viewer shows.
    @elseif(failure == Failure.UNSUPPORTED)
      This text uses Formex the viewer can't read yet.
    @else
      The text couldn't be made.
    @endif
    <a href="${eurLex}">Read it on EUR-Lex</a>.
  </p>
</div>
