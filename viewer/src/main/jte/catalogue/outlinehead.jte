@param int total
<%-- A comparison's outline's head (Design D-44, D-25, D-33): what it lists, a button collapsing it to
     a rail of the parts with their counts; under it, the difference the reader is at, as they read
     (js/outline.js): Difference 663 of 1,828 · Annex IV, with ↑ ↓ to the one before and after --%>
<p class="outline-head"><span>Outline</span><span>differences</span><button type="button" class="outline-rail" aria-pressed="false" aria-label="Collapse the outline" title="Collapse the outline">⇤</button></p>
@if(total > 0)
  <p class="outline-follow" data-total="${total}"><span class="where">${total == 1 ? "1 difference" : String.format(java.util.Locale.ROOT, "%,d differences", total)}</span><a href="#difference-1" class="up" aria-label="Previous difference">↑</a><a href="#difference-1" class="down" aria-label="Next difference">↓</a></p>
@endif
