@import dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.web.view.ArrivingView
@param ArrivingView arriving
<%-- Versions that appeared since the act was prepared, being prepared (ArrivingView): a notice,
     listening to the act's preparation (hx-sse:connect); once done, this address is asked again and
     its page swapped in for this one, with them --%>
<div class="arriving" role="status" hx-get="${arriving.self()}" hx-trigger="done" hx-target="#page" hx-select="#page" hx-swap="outerHTML">
  <p>${arriving.notice()}</p>
  <div hx-sse:connect="${arriving.events()}" hx-sse:close="done" hidden></div>
</div>
