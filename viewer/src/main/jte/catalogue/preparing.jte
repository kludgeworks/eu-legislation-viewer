@import dev.groknull.kludgeworks.eu.legislation.viewer.site.Frame
@import dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.web.view.MastheadView
@import dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.web.view.PreparingView
@param Frame frame
@param String title
@param MastheadView masthead
@param PreparingView preparing
<%-- Shows an act's page while the act is being prepared in its language. The page can be for a
     version or for a comparison. It has the act's head, with a mode switch that is not a link yet.
     Then come the progress panel and what is already known about the act.
     The stream (hx-sse:connect) pushes the progress and the steps when they change.
     htmx swaps each fragment by its id (hx-partial). When the stream sends done, htmx requests this
     address again with prepared set, so that the page's content fades in. It swaps that page in for
     this one, at the same address.
     Beside the progress is a link back to the reader's last search results, if there are any.
     Without JavaScript, the page reloads every few seconds. The noscript refresh is in the head
     for this reason: a boosted request, such as Try again, parses what it swaps in without scripts.
     A noscript in the page body would then become active and reload the page. --%>
@template.layout.page(frame = frame, title = title, head = @`<noscript><meta http-equiv="refresh" content="5"></noscript>`, content = @`
  <div id="page" hx-boost:inherited="true">
    @template.catalogue.masthead(masthead = masthead)
    <div class="preparing" hx-get="${preparing.self()}" hx-trigger="done" hx-vals='{"prepared": true}' hx-target="#page" hx-select="#page" hx-swap="outerHTML">
      <section class="panel" aria-labelledby="preparing-head">
        <p id="told" class="visually-hidden" aria-live="polite">${preparing.progress().counts()}</p>
        <h2 id="preparing-head">Preparing this act</h2>
        <p>${preparing.asked()}</p>
        @template.catalogue.preparing.progress(progress = preparing.progress())
        <p class="leave">You can leave: this page keeps its address and finishes without you.</p>
      </section>
      <aside class="known" aria-labelledby="known-head">
        <h2 id="known-head">Already known about this act</h2>
        <dl>
          @for(var fact : preparing.known())
            <dt>${fact.term()}</dt>
            <dd>${fact.detail()}</dd>
          @endfor
        </dl>
        <h2>While you wait</h2>
        <p><a href="${preparing.eurLex()}" rel="external">Read it on EUR-Lex<span aria-hidden="true"> ↗</span><span class="visually-hidden"> (opens EUR-Lex)</span></a>: the official site, without comparison.</p>
        @if(frame.results() != null)
          <p><a href="${frame.results().href()}">Back to the results for “${frame.results().words()}”</a></p>
        @endif
      </aside>
      <div hx-sse:connect="${preparing.events()}" hx-sse:close="done" hidden></div>
    </div>
  </div>
`)
