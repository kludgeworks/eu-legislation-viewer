@import dev.groknull.kludgeworks.eu.legislation.viewer.site.Frame
@import dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.web.view.HistoryView
@import dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.web.view.MastheadView
@param Frame frame
@param MastheadView masthead
@param HistoryView history
<%-- Shows the history of an act on a day. HistoryView supplies the data. The page has the head of
     the act, the day with its date form, and one line with the state of the act itself. Then comes
     the list of the amending acts, newest first. Each row has the act, its Official Journal link,
     its state on the day, its dates with the clause, and a link to its changes. Amber is only on
     a row with a change that does not apply on the day. A period can limit the list; "Show all"
     removes the limit. The corrigenda are in their own list, because a corrigendum has no day from
     which it applies. htmx boosts the links and the forms, as on the page of a version. --%>
@template.layout.page(frame = frame, title = history.title(), content = @`
  <div id="page" data-view="history" hx-boost:inherited="target:#page select:#page swap:outerHTML">
    @template.catalogue.skeletons()
    @template.catalogue.masthead(masthead = masthead)
    <section class="history" aria-label="History">
      <form class="on" method="get" action="${history.action()}">
        <label for="on">The law on</label>
        <input id="on" name="on" type="date" value="${history.on()}" required>
        <button>Go</button>
        @if(history.today() != null)<a class="quiet" href="${history.today()}">Today</a>@endif
      </form>
      <p class="status"><b>On ${history.day()}.</b> ${history.state()} <a href="${history.read()}" data-view="read">Read the law on this day</a></p>
      <div class="history-head">
        <h2 id="amending">Amending acts, newest first</h2>
        <form class="period" method="get" action="${history.action()}">
          <input type="hidden" name="on" value="${history.on()}">
          <label for="from">Dated from</label>
          <input id="from" name="from" type="date" value="${history.from()}">
          <label for="to">to</label>
          <input id="to" name="to" type="date" value="${history.to()}">
          <button>Limit</button>
          @if(history.showAll() != null)<a href="${history.showAll()}">Show all ${history.all()}</a>@endif
        </form>
      </div>
      @if(history.rows().isEmpty())
        <p class="none">@if(history.all() == 0)No act changed this act.@else No amending act in this period.@endif</p>
      @else
        <table class="acts" aria-labelledby="amending">
          <thead><tr><th scope="col">Act</th><th scope="col">State on ${history.day()}</th><th scope="col">Its changes</th></tr></thead>
          <tbody>
            @for(var row : history.rows())
              <tr>
                <th scope="row">
                  <span class="cited">${row.cited()}</span> · <a class="quiet" href="${row.journalHref()}" rel="external">${row.journal()}<span aria-hidden="true"> ↗</span><span class="visually-hidden"> (opens EUR-Lex)</span></a>
                  @if(row.subject() != null)<p class="subject" lang="${row.subjectLanguage()}">${row.subject()}</p>@endif
                </th>
                <td>
                  <span class="state ${row.tone()}">${row.state()}</span>
                  <p>${row.dates()}</p>
                  @if(row.counts() != null)<p class="counts">${row.counts()}</p>@endif
                </td>
                <td><a href="${row.href()}">${row.changes()}</a></td>
              </tr>
            @endfor
          </tbody>
        </table>
      @endif
      @if(history.compare() != null)
        <p><a class="button" href="${history.compare()}" data-view="changes">Compare the law on the two days of the period</a></p>
      @endif
      @if(!history.corrigenda().isEmpty())
        <h2 id="corrigenda">Corrigenda</h2>
        <p class="hint">A corrigendum corrects the text as published. It has no day from which it applies.</p>
        @template.catalogue.corrigenda(corrigenda = history.corrigenda())
      @endif
    </section>
  </div>
`)
