@import dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.web.view.ArrivingView
@param ArrivingView arriving
<%-- Shows a notice about versions that appeared after the act was prepared. ArrivingView supplies
     the text. The page does not change while the reader reads. The new versions show the next
     time the page loads. --%>
<div class="arriving" role="status">
  <p>${arriving.notice()}</p>
</div>
