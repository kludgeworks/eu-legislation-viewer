@param String changes
<%-- Shows the slot of the changes when both texts are built. htmx loads the changes from the
     comparison module's address (changes) when this element loads, in the view that the page's
     address asks for. The comparison module calculates the comparison if no earlier request did. While it loads, the reader first sees nothing, then a bar, then the page's
     shape. --%>
<div id="text" hx-get="${changes}" hx-trigger="load" hx-swap="innerHTML">
  <div class="loading" role="status" aria-label="Comparing the two texts">
    <div class="loading-bar"></div>
    <div class="skeleton" aria-hidden="true"><i></i><i></i><i></i><i></i><i></i></div>
  </div>
  <noscript><p><a href="${changes}">The changes</a></p></noscript>
</div>
