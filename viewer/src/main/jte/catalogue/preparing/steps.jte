@import dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.web.view.PreparingView.Step
@import java.util.List
@param List<Step> steps
<%-- The preparation's steps as they happened (PreparingView.Step): what was done, never the
     server's log. The stream swaps it (#steps), so the list around it stays open --%>
<ol id="steps" class="steps-log">
  @for(var step : steps)
    <li><time>${step.time()}</time> ${step.text()}</li>
  @else
    <li>Nothing has happened yet.</li>
  @endfor
</ol>
