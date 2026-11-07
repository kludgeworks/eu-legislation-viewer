@param String changes
<%-- Both texts built: their Changes view, loaded from beside the page (worked out in the comparison
     module, if no one has yet). While it loads (Design: loading): nothing at first, then a bar,
     then the page's shape --%>
<div id="text" hx-get="${changes}" hx-trigger="load" hx-swap="innerHTML">
  <div class="loading" role="status" aria-label="Comparing the two texts">
    <div class="loading-bar"></div>
    <div class="skeleton" aria-hidden="true"><i></i><i></i><i></i><i></i><i></i></div>
  </div>
  <noscript><p><a href="${changes}">The changes</a></p></noscript>
</div>
