@import dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.web.view.PreparingView.Step
@import java.util.List
@param List<Step> steps
<%-- Shows the preparation's steps in the order they happened (PreparingView.Step). It says what
     was done. It never shows the server's log. The stream swaps this list by its id (#steps), so
     the details element around it stays open. --%>
<ol id="steps" class="steps-log">
  @for(var step : steps)
    <li><time>${step.time()}</time> ${step.text()}</li>
  @else
    <li>Nothing has happened yet.</li>
  @endfor
</ol>
