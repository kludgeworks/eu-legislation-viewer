@param String changes
<%-- Both texts built: their Changes view, loaded from beside the page (worked out in the comparison
     module, if no one has yet) --%>
<div id="text" hx-get="${changes}" hx-trigger="load" hx-swap="innerHTML">
  <p class="status">Comparing the two texts…</p>
  <noscript><p><a href="${changes}">The changes</a></p></noscript>
</div>
