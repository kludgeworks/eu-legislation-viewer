@import dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.web.view.ReadingView
@param ReadingView reading
<%-- Shows the Reading card: the law on the day the reader asked for. ReadingView supplies the
     data. The day is the headline. Under it: which version that is and how long it applied, the
     acts that made it, and the status lines where the text is not the law on the day. One line is
     for one act: the act, its state on the day, the date and the count of provisions. An amending
     act is a link to its page. Three lines show, and "Show all" opens the others.
     Then the version's consolidated CELEX, small, with a copy button.
     Before the act's first version there is no version: a dash stands for it, and the panel in
     place of the text says why. A GET form asks for the law on another day; js/datepicker.js turns
     its date box into a calendar that rings the days a new version starts. A link goes to the
     comparison with the version before, or after for the first version. --%>
<section class="selection" aria-label="Reading">
  <article class="card">
    <span class="card-kind">The law on</span>
    <span class="card-day">${reading.day()}</span>
    @if(reading.version() != null)
      <span>${reading.version()}</span>
      <span title="${reading.published()}">${reading.made()}</span>
    @else
      <span>—</span>
    @endif
    @if(reading.status().size() > 0)
      <div id="status" class="status-lines" role="note">
        <p><b>On ${reading.status().day()}.</b></p>
        @for(var line : reading.status().lines())
          <p><b>@if(line.href() != null)<a href="${line.href()}">${line.act()}</a>@else${line.act()}@endif</b> ${line.words()}</p>
        @endfor
        @if(!reading.status().more().isEmpty())
          <details>
            <summary>Show all ${reading.status().size()}</summary>
            @for(var line : reading.status().more())
              <p><b>@if(line.href() != null)<a href="${line.href()}">${line.act()}</a>@else${line.act()}@endif</b> ${line.words()}</p>
            @endfor
          </details>
        @endif
      </div>
    @endif
    @if(reading.celex() != null)
      <copy-text class="card-celex" data-text="${reading.celex()}"><a class="mono quiet" href="${reading.eurLex()}" rel="external">${reading.celex()}<span aria-hidden="true"> ↗</span><span class="visually-hidden"> (opens EUR-Lex)</span></a> <button type="button" class="link">copy</button></copy-text>
    @endif
    <date-picker data-starts="${reading.starts()}">
      <form class="on" method="get" action="${reading.action()}">
        <label for="on">The law on</label>
        <input id="on" name="on" type="date" value="${reading.on()}" required>
        <button>Go</button>
      </form>
    </date-picker>
  </article>
  @if(reading.compare() != null)
    <a class="button" href="${reading.compare()}">${reading.comparing()}</a>
  @endif
</section>
