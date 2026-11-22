@param String differences
@param String said
<%-- Shows the slot of the differences when both texts are built. htmx loads the differences from the
     comparison module's address (differences) when this element loads, in the view that the page's
     address asks for. For one provision, the address is that of its wording on the two days. The comparison module calculates the comparison if no earlier request did.
     While it loads, the reader first sees nothing, then a bar, then the page's shape. A polite
     status line says what is being compared (said): "Comparing the law on 1 July 2021 with the law
     on 31 December 2021…". --%>
<div id="text" hx-get="${differences}" hx-trigger="load" hx-swap="innerHTML">
  <div class="loading" role="status" aria-busy="true">
    <p class="visually-hidden">${said}</p>
    <div class="loading-bar"></div>
    <div class="skeleton noted" aria-hidden="true"><i></i><b></b><i></i><i></i><b></b><i></i></div>
  </div>
  <noscript><p><a href="${differences}">The differences</a></p></noscript>
</div>
