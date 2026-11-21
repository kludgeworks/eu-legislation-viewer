@import dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.web.view.ProvisionView
@param ProvisionView provision
<%-- Shows the history of one provision: the acts that changed it. ProvisionView supplies the data.
     js/tags.js loads it into the text of the Read page, below the provision. One line is for one
     change, newest act first: the act as a link to its page, what it did, the state of the change
     on the day with the date and the clause, and a link to the change on the page of the act. A
     change that does not apply on the day has an amber rule. Where the data names no act, the box
     says so, with a link to the first text that has the provision. If two or more amending acts
     changed the provision, a link opens the comparison of its wording before the first of them and after the last. --%>
<aside class="provision-history" aria-label="${provision.heading()}">
  <p class="provision-head"><b>${provision.heading()}</b>@if(provision.count() != null) · ${provision.count()}@endif</p>
  @if(!provision.lines().isEmpty())
    <ul>
      @for(var line : provision.lines())
        <li class="${line.tone()}"><b>@if(line.href() != null)<a href="${line.href()}">${line.act()}</a>@else${line.act()}@endif</b> ${line.words()}@if(line.note() != null) ${line.note()}@endif@if(line.change() != null) <a href="${line.change()}">See this change</a>@endif</li>
      @endfor
    </ul>
  @endif
  @if(provision.unknown() != null)
    <p>${provision.unknown().words()}@if(provision.unknown().href() != null) <a href="${provision.unknown().href()}" rel="external">${provision.unknown().link()}<span aria-hidden="true"> ↗</span><span class="visually-hidden"> (opens EUR-Lex)</span></a>@endif</p>
  @endif
  @if(provision.compare() != null)
    <p><a class="button" href="${provision.compare()}">See how its wording changed</a></p>
  @endif
</aside>
