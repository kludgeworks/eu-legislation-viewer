@import dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.web.chrome.Chrome
@import dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.web.view.MastheadView
@import dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.web.view.PreparingView
@import dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.web.view.TimelineView
@param Chrome chrome
@param String title
@param MastheadView masthead
@param TimelineView timeline
@param PreparingView preparing
<%-- An act's page, a version's or a comparison's, while the act is being prepared in its language
     (Design: Preparing an act): its head, the mode switch not yet a link, its timeline with each
     version's text as it stands, the progress panel, and what is already known about the act. The
     stream (hx-sse:connect) pushes the timeline, the progress and the steps as they change, each
     swapped by its id (hx-partial); once done, this address is asked again (prepared, for the
     receipt) and its page swapped in for this one, at the same address. Without JavaScript, the
     page reloads --%>
@template.layout.page(chrome = chrome, title = title, content = @`
  <div id="page" hx-boost:inherited="true">
    @template.catalogue.masthead(masthead = masthead)
    @template.catalogue.timeline(timeline = timeline)
    <div class="preparing" hx-get="${preparing.self()}" hx-trigger="done" hx-vals='{"prepared": true}' hx-target="#page" hx-select="#page" hx-swap="outerHTML">
      <section class="panel" aria-labelledby="preparing-head">
        <h2 id="preparing-head">Preparing this act</h2>
        <p>${preparing.asked()}</p>
        @template.catalogue.preparing.progress(progress = preparing.progress())
        <details class="log">
          <summary>Show each step as it happened</summary>
          @template.catalogue.preparing.steps(steps = preparing.steps())
        </details>
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
        <p><a href="${preparing.eurLex()}">Read it on EUR-Lex</a>: the official site, without comparison.</p>
      </aside>
      <div hx-sse:connect="${preparing.events()}" hx-sse:close="done" hidden></div>
      <noscript><meta http-equiv="refresh" content="5"></noscript>
    </div>
  </div>
`)
