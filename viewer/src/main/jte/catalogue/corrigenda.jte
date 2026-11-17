@import dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.web.view.HistoryView
@import java.util.List
@param List<HistoryView.Corrigendum> corrigenda
<%-- Shows a list of corrigenda: each with the day the Official Journal published it, its link, and
     the provisions that it corrects, where Cellar lists them. --%>
<ul class="corrigenda" aria-labelledby="corrigenda">
  @for(var each : corrigenda)
    <li>
      <span class="cited">${each.cited()}</span>
      @if(each.published() != null) · ${each.published()}@endif
      · <a class="quiet" href="${each.journalHref()}" rel="external">${each.journal()}<span aria-hidden="true"> ↗</span><span class="visually-hidden"> (opens EUR-Lex)</span></a>
      @if(each.places() != null)<p>${each.places()}</p>@endif
    </li>
  @endfor
</ul>
