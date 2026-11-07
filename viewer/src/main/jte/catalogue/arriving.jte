@import dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.web.view.ArrivingView
@param ArrivingView arriving
<%-- Versions that appeared since the act was prepared, being prepared (ArrivingView, D-61): a
     notice; the page stays as it is, and has them the next time it loads --%>
<div class="arriving" role="status">
  <p>${arriving.notice()}</p>
</div>
