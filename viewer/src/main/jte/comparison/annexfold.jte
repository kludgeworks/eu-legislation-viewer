@import dev.groknull.kludgeworks.eu.legislation.viewer.comparepage.ComparePage
@import dev.groknull.kludgeworks.eu.legislation.viewer.comparison.web.view.ChangesView
@param ChangesView.Shown shown
@param ComparePage page
<%-- Shows an annex inserted whole as a box that says how many provisions it has, with three ways
     to see it: its outline, which loads the annex's units without their text; the annex itself,
     which loads it from the address of its difference; and the annex on its own in Read, in the
     version at the To end. The outline and the annex open in place, under the box. The annex is
     not sent until the reader asks: one annex can be tens of MB. --%>
!{var eId = shown.difference().eId();}
<div class="annex-inserted">
  <p class="annex-head"><span class="annex-size">Inserted whole · ${shown.size()}</span>
    · <button type="button" class="link" hx-get="${page.outline(eId)}" hx-target="next .fold-text" hx-swap="innerHTML">Show its outline</button>
    · <button type="button" class="link" hx-get="${shown.part(page)}" hx-target="next .fold-text" hx-swap="innerHTML">Show it here</button>
    · <a href="${page.read(eId)}">Read it on its own</a></p>
  <div class="fold-text"></div>
</div>
