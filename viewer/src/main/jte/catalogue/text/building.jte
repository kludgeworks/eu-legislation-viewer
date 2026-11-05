@import dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.Preparation
@param Preparation preparation
@param String self
@param String events
<%-- The act being prepared in the language, every version of it, before any of its pages shows a
     text: how it stands, pushed over the act's stream (events, htmx's SSE extension) as it changes;
     once it is done, this URL asked again for the text. Without JavaScript, the page reloads --%>
<div id="text" hx-get="${self}" hx-trigger="done" hx-swap="outerHTML">
  <div class="preparing" hx-sse:connect="${events}" hx-sse:close="done" aria-live="polite">
    @template.catalogue.text.progress(preparation = preparation)
  </div>
  <noscript><meta http-equiv="refresh" content="5"></noscript>
</div>
