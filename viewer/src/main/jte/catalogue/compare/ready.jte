@param String changes
<%-- Shows the Changes view when both texts are built. htmx loads the view from the changes
     address when this element loads. The comparison module calculates the view if no earlier
     request did. While it loads, the reader first sees nothing, then a bar, then the page's
     shape. --%>
<div id="text" hx-get="${changes}" hx-trigger="load" hx-swap="innerHTML">
  <div class="loading" role="status" aria-label="Comparing the two texts">
    <div class="loading-bar"></div>
    <div class="skeleton" aria-hidden="true"><i></i><i></i><i></i><i></i><i></i></div>
  </div>
  <noscript><p><a href="${changes}">The changes</a></p></noscript>
</div>
