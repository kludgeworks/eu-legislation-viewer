@param int total
<%-- Shows the head of a comparison's outline. It says what the outline lists. A button collapses
     the outline to a rail of the parts with their counts. Under the head, js/outline.js shows the
     difference that the reader is at, and updates it as they read. Example: "Difference 663 of
     1,828 · Annex IV". The ↑ and ↓ links go to the difference before and after. --%>
<p class="outline-head"><span>Outline</span><span>differences</span><button type="button" class="outline-rail" aria-pressed="false" aria-label="Collapse the outline" title="Collapse the outline">⇤</button></p>
@if(total > 0)
  <p class="outline-follow" data-total="${total}"><span class="where">${total == 1 ? "1 difference" : String.format(java.util.Locale.ROOT, "%,d differences", total)}</span><a href="#difference-1" class="up" aria-label="Previous difference">↑</a><a href="#difference-1" class="down" aria-label="Next difference">↓</a></p>
@endif
