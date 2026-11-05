@param String changes
<%-- Both texts built: their Changes view, loaded from the comparison module (worked out there, if
     no one has yet) --%>
<div id="text" hx-get="${changes}" hx-trigger="load" hx-swap="innerHTML">
  <p class="status">Comparing the two texts…</p>
  <noscript><p><a href="${changes}">The changes</a></p></noscript>
</div>
