@import dev.groknull.kludgeworks.eu.legislation.viewer.comparison.web.view.ChangesView
@import dev.groknull.kludgeworks.eu.legislation.viewer.comparison.web.view.OutlineView
@import dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.web.view.CompareView
@import dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.web.view.SidenoteView
@import dev.groknull.kludgeworks.eu.legislation.viewer.content.web.LegalRenderer
@import dev.groknull.kludgeworks.eu.legislation.viewer.content.Element
@import java.util.List
@import java.util.Map
@param ChangesView changes
@param OutlineView outline
@param Map<Integer, SidenoteView> sidenotes
@param Map<String, Element> neighbours
@param Map<String, SidenoteView.History> histories
@param LegalRenderer html
@param String from
@param String to
@param List<CompareView.ViewLink> views
@param CompareView.Only only = null
@param String preamble = ""
<%-- A comparison's Synopsis view (Design: Mode: Compare, Synopsis): its controls, then a ledger of
     the two texts aligned by provision, From's CELEX and To's heading their columns: each provision
     that differs a row, its unit marked in both, the words it lost struck in From, those it gained
     underlined in To, Not in this version where a text hasn't it; what changed it beside them (as
     a sidenote says it, by number), with ↑ ↓ to the difference before and after; the opening of
     the unit the differences are in as a row of context; the units the same in both before each, in
     the unit it is in, the one beside each difference a row as the later text has it (neighbours,
     by eId), with what last made it as it is (histories), those between them folded; those an act
     filter leaves out, folded. Beside it, the later text's outline, its differences counted --%>
!{var total = changes.numbers().size();}
<div class="outlined">
<nav class="outline counted" aria-label="Outline">
  <p class="outline-head"><span>Outline</span><span>differences</span></p>
  @template.comparison.outline(entries = outline.entries())
</nav>
<div class="changes synopsis">
  @template.catalogue.controls(changes = changes, views = views, only = only)
  <div class="ledger legal-text legal-text-consolidated" role="table" aria-label="The two versions, aligned by provision">
    <div class="ledger-head" role="row"><div role="columnheader">Provision</div><div role="columnheader">From<span class="celex">${from}</span></div><div role="columnheader">To<span class="celex">${to}</span></div><div role="columnheader">What changed it</div></div>
    @for(var passage : changes.passages())
      @if(passage instanceof ChangesView.Fold fold && fold.same() && fold.beside().stream().allMatch(unit -> neighbours.containsKey(unit.eId())))
        !{var between = fold.between();}
        @for(var each : fold.beside())
          @template.catalogue.neighbour(unit = each, element = neighbours.get(each.eId()), history = histories.get(each.eId()), html = html)
          @if(between != null && each == fold.beside().getFirst())
            <div class="ledger-fold" role="row"><span role="cell">${between.label()} — ${between.note()}</span></div>
          @endif
        @endfor
      @elseif(passage instanceof ChangesView.Fold fold)
        <div class="ledger-fold" role="row"><span role="cell">${fold.label()} — ${fold.note()}</span></div>
      @elseif(passage instanceof ChangesView.NoPreamble)
        <div class="ledger-fold" role="row"><span role="cell">Preamble — ${preamble}</span></div>
      @elseif(passage instanceof ChangesView.Lead lead)
        <div class="ledger-row ledger-lead" role="row"><div role="cell"></div><div class="from" role="cell">${html.element(lead.unit())}</div><div class="to" role="cell">${html.element(lead.unit())}</div><div role="cell"></div></div>
      @elseif(passage instanceof ChangesView.Shown shown)
        !{var sidenote = sidenotes.get(shown.number());}
        !{var unit = html.element(shown.unit());}
        <div class="ledger-row difference-${shown.kind()}${only != null && sidenote != null && !sidenote.candidates().isEmpty() ? " unknown" : ""}" role="row" id="difference-${shown.number()}">
          <div class="provision" role="cell">${shown.cited()}<span class="n">difference ${shown.number()} of ${total}</span></div>
          <div class="from" role="cell">@if(shown.kind().equals("inserted"))<p class="absent">Not in this version</p>@else${unit}@endif</div>
          <div class="to" role="cell">@if(shown.kind().equals("deleted"))<p class="absent">Not in this version</p>@else${unit}@endif</div>
          <div class="what" role="cell">
            @template.catalogue.sidenote(shown = shown, sidenote = sidenote)
            <nav class="steps" aria-label="Differences">@if(shown.number() > 1)<a href="#difference-${shown.number() - 1}" aria-label="Previous difference">↑</a>@else<span aria-hidden="true">↑</span>@endif@if(shown.number() < total)<a href="#difference-${shown.number() + 1}" aria-label="Next difference">↓</a>@else<span aria-hidden="true">↓</span>@endif</nav>
          </div>
        </div>
      @endif
    @endfor
  </div>
</div>
</div>
