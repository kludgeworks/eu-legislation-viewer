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
@param String same = ""
<%-- Shows a comparison's Synopsis view. The controls come first. Then a ledger aligns the two
     texts by provision. From's CELEX and To's CELEX are in the column heads.
     Each provision that differs is a row, with its unit marked in both texts. Words that it lost are
     struck through in From. Words that it gained are underlined in To. "Not in this version" shows
     where a text does not have the provision. The last column says what changed it, as a sidenote
     says it (sidenotes, by number). The ↑ and ↓ links go to the difference before and after.
     The opening of the unit that holds the differences is a row of context.
     Before each difference come the units of its parent unit that are the same in both texts. The
     unit next to the difference is a row, as the later text has it (neighbours, by eId). That row also says what last made the unit as it is
     (histories). The units between them are in a fold. Units that an act filter leaves out are
     also in a fold.
     The later text's outline is beside the view, with a count of differences for each unit. --%>
!{var total = changes.numbers().size();}
<div class="outlined">
<comparison-outline>
<nav class="outline counted" aria-label="Outline">
  @template.catalogue.outlinehead(total = changes.numbers().size())
  @template.comparison.outline(entries = outline.entries())
</nav>
</comparison-outline>
<div class="changes synopsis">
  @template.catalogue.controls(changes = changes, views = views, only = only, corrected = sidenotes.values().stream().anyMatch(SidenoteView::corrected))
  <div class="ledger legal-text legal-text-consolidated" role="table" aria-label="The two versions, aligned by provision">
    <div class="ledger-head" role="row"><div role="columnheader">Provision</div><div role="columnheader">From<span class="celex">${from}</span></div><div role="columnheader">To<span class="celex">${to}</span></div><div role="columnheader">What changed it</div></div>
    @for(var passage : changes.passages())
      @if(passage instanceof ChangesView.Fold fold && fold.same() && fold.beside().stream().allMatch(unit -> neighbours.containsKey(unit.eId())))
        !{var between = fold.between();}
        @for(var each : fold.beside())
          @template.catalogue.neighbour(unit = each, element = neighbours.get(each.eId()), history = histories.get(each.eId()), html = html)
          @if(between != null && each == fold.beside().getFirst())
            @template.catalogue.ledgerfold(fold = between, same = same)
          @endif
        @endfor
      @elseif(passage instanceof ChangesView.Fold fold)
        @template.catalogue.ledgerfold(fold = fold, same = same)
      @elseif(passage instanceof ChangesView.NoPreamble)
        <div class="ledger-fold" role="row"><span role="cell">Preamble — ${preamble}</span></div>
      @elseif(passage instanceof ChangesView.Lead lead)
        <div class="ledger-row ledger-lead" role="row"><div role="cell"></div><div class="from" role="cell">${html.element(lead.unit())}</div><div class="to" role="cell">${html.element(lead.unit())}</div><div role="cell"></div></div>
      @elseif(passage instanceof ChangesView.Shown shown)
        !{var sidenote = sidenotes.get(shown.number());}
        !{var unit = html.element(shown.unit());}
        <div class="ledger-row difference-${shown.kind()}${only != null && sidenote != null && !sidenote.candidates().isEmpty() ? " unknown" : ""}${sidenote != null && sidenote.corrected() ? " corrected" : ""}" role="row" id="difference-${shown.number()}">
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
