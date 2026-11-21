@import dev.groknull.kludgeworks.eu.legislation.viewer.site.Frame
@import dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.web.view.ActLookupView
@param Frame frame
@param String title
@param ActLookupView lookup
<%-- Shows an act's page while the viewer looks the act up at the Publications Office, because the
     catalogue does not have it yet. The page keeps the address that the reader asked for.
     The stream (hx-sse:connect) says when the lookup ends:
     - done: the act is found. htmx requests this address again and swaps in its page, which can be
       the preparing page of the act. An address that redirects, such as the act without a date,
       answers this request with HX-Redirect (X-Act-Found), so the browser shows the address of the
       page.
     - a message: no act has the address, or the Publications Office did not answer. It replaces the
       waiting panel, and the stream with it (hx-partial).
     After 15 s, a second line says that the Publications Office is slow. Without JavaScript, the
     page reloads every few seconds. --%>
@template.layout.page(frame = frame, title = title, head = @`<noscript><meta http-equiv="refresh" content="5"></noscript>`, content = @`
  <div id="page" class="act-lookup" hx-boost:inherited="true">
    <div id="act-lookup" class="looking" role="status" hx-get="${lookup.self()}" hx-trigger="done" hx-headers='{"X-Act-Found": "true"}' hx-target="#page" hx-select="#page" hx-swap="outerHTML">
      <p class="none">${lookup.line()}</p>
      <p class="slow">The Publications Office is slow to answer.</p>
      <div hx-sse:connect="${lookup.events()}" hx-sse:close="done" hidden></div>
    </div>
  </div>
`)
