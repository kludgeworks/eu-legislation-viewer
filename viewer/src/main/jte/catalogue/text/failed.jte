@import dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.web.view.FailureView
@param FailureView failure
<%-- Why the text couldn't be made, in its place (FailureView): what happened, which version, and
     only what can help. Try again posts to the page, boosted as its links are, which answers with
     the page again: preparing, once more --%>
<div id="text">
  <section class="failure${failure.fault() ? " fault" : ""}" aria-labelledby="failure">
    <div class="failure-head">
      <h2 id="failure">${failure.heading()}</h2>
      <p>${failure.words()}</p>
    </div>
    @if(!failure.facts().isEmpty())
      <dl>
        @for(var fact : failure.facts())
          <dt>${fact.term()}</dt><dd>${fact.detail()}</dd>
        @endfor
      </dl>
    @endif
    <div class="failure-actions">
      @if(failure.retry() != null)
        <form method="post" action="${failure.retry()}"><button type="submit">Try again</button></form>
      @endif
      @for(var action : failure.actions())
        <a class="${action.button() ? "button" : null}" href="${action.href()}">${action.label()}</a>
      @endfor
    </div>
    @if(failure.reference() != null)
      <p class="reference">If you report this, quote <code>${failure.reference()}</code>: it tells us which version, in which language.</p>
    @endif
  </section>
</div>
