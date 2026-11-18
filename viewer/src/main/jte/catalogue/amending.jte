@import dev.groknull.kludgeworks.eu.legislation.viewer.site.Frame
@import dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.web.view.AmendingView
@import dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.web.view.MastheadView
@param Frame frame
@param MastheadView masthead
@param AmendingView amending
<%-- Shows the page of one amending act: what it changed in the act, and the state of each change on
     a day. AmendingView supplies the data. Under the head of the act come a link back to the
     history, the heading, the day with its date form, and one line with the state of the amending
     act. Where the data does not give the day from which the act applies, the line ends with a link
     to the article that sets the day. The groups are the changes that apply from the same day. Each is a link that limits the page to
     its changes, and "All changes" removes the limit. The page shows them only where the changes
     are in two or more groups. The changes are in the order of the text. Changes that follow each other and have
     the same state are one run: the run says the state, the date and the clause one time, and then
     lists its provisions. Under a provision is the wording of its change: the provision as it reads
     in the first text with the act, with the inserted and the deleted words marked. htmx loads it
     from the comparison module when the fold of the provision opens. The folds are open when the
     page has few changes, and then htmx loads each wording when the page loads: a fold that is
     open at the start gives no toggle event that htmx gets. A provision whose wording the texts do not give shows by its name only.
     Each change has an anchor, so the history of a provision on Read can link to it.
     A run that does not apply on the day has an amber rule.
     Where the first version with the act integrates another act too, htmx loads a separate list
     below the runs: the changes that this act or the other act made, where the data does not say
     which. Its heading is "By this act or by …". A run that
     applies has no rule. Where the data does not give the day, the rule is grey and broken. --%>
@template.layout.page(frame = frame, title = amending.title(), content = @`
  <div id="page" data-view="history" hx-boost:inherited="target:#page select:#page swap:outerHTML">
    @template.catalogue.skeletons()
    @template.catalogue.masthead(masthead = masthead)
    <section class="history amending" aria-labelledby="amending-head">
      <p class="crumb"><a href="${amending.history()}">‹ History of ${amending.base()}</a></p>
      <h2 id="amending-head">${amending.heading()}</h2>
      <p class="subject">@if(amending.subject() != null)<span lang="${amending.subjectLanguage()}">${amending.subject()}</span> · @endif<a class="quiet" href="${amending.journalHref()}" rel="external">${amending.journal()}<span aria-hidden="true"> ↗</span><span class="visually-hidden"> (opens EUR-Lex)</span></a></p>
      @template.catalogue.day(day = amending.picker())
      <p class="status"><b>On ${amending.day()}.</b> ${amending.state()} ${amending.dates()}@if(amending.clause() != null) <a href="${amending.clause().href()}" rel="external">${amending.clause().label()}<span aria-hidden="true"> ↗</span><span class="visually-hidden"> (opens EUR-Lex)</span></a>@endif</p>
      @if(!amending.groups().isEmpty())
        <nav class="groups" aria-label="Changes by the day from which they apply">
          @for(var group : amending.groups())
            <a class="group ${group.tone()}" href="${group.href()}" aria-current="${group.on() ? "true" : null}">${group.label()}<span class="count">${group.count()}</span></a>
          @endfor
        </nav>
      @endif
      @if(!amending.runs().isEmpty())
        @if(!amending.ordered())
          <p class="hint">The text of ${amending.base()} is not prepared yet. The changes are in the order of the data, not of the text.</p>
        @endif
        @for(var run : amending.runs())
          <section class="run ${run.tone()}">
            @if(run.note() != null)<p class="note">${run.note()} <span class="size">${run.size()}</span></p>@endif
            <ul class="changes-list${run.worded() ? " worded" : ""}">
              @for(var change : run.changes())
                @if(change.wording() != null)
                  <li id="${change.anchor()}">
                    <details class="wording" open="${amending.open()}" hx-get="${change.wording()}" hx-trigger="${amending.open() ? "load" : "toggle once"}" hx-target="find .wording-text" hx-swap="innerHTML">
                      <summary><span class="place">${change.place()}</span> <span class="made">${change.made()}</span><span class="show"> · <u>wording</u></span></summary>
                      <div class="wording-text"><p class="status">Loading…</p></div>
                    </details>
                  </li>
                @else
                  <li id="${change.anchor()}"><span class="place">${change.place()}</span> <span class="made">${change.made()}</span></li>
                @endif
              @endfor
            </ul>
          </section>
        @endfor
      @endif
      @if(amending.either() != null)
        <div hx-get="${amending.either()}" hx-trigger="load" hx-swap="outerHTML"></div>
      @endif
      @if(!amending.corrigenda().isEmpty())
        <h2 id="corrigenda">Corrigenda of this act</h2>
        @template.catalogue.corrigenda(corrigenda = amending.corrigenda())
      @endif
    </section>
  </div>
`)
