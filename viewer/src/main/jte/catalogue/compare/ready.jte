@param String changes
@param String said
<%-- Shows the slot of the changes when both texts are built. htmx loads the changes from the
     comparison module's address (changes) when this element loads, in the view that the page's
     address asks for. The comparison module calculates the comparison if no earlier request did.
     While it loads, the reader first sees nothing, then a bar, then the page's shape. A polite
     status line says what is being compared (said): "Comparing the law on 1 July 2021 with the law
     on 31 December 2021…". --%>
<div id="text" hx-get="${changes}" hx-trigger="load" hx-swap="innerHTML">
  <div class="loading" role="status" aria-busy="true">
    <p class="visually-hidden">${said}</p>
    <div class="loading-bar"></div>
    <div class="skeleton noted" aria-hidden="true"><i></i><b></b><i></i><i></i><b></b><i></i></div>
  </div>
  <noscript><p><a href="${changes}">The changes</a></p></noscript>
</div>
