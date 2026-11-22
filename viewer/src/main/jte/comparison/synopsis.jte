@import dev.groknull.kludgeworks.eu.legislation.viewer.comparison.web.view.SidenotePieces
@import dev.groknull.kludgeworks.eu.legislation.viewer.comparison.web.view.DifferencesView
@import dev.groknull.kludgeworks.eu.legislation.viewer.comparison.web.view.OutlineView
@import dev.groknull.kludgeworks.eu.legislation.viewer.comparison.web.view.ControlsView
@import dev.groknull.kludgeworks.eu.legislation.viewer.comparison.web.view.SidenoteView
@import dev.groknull.kludgeworks.eu.legislation.viewer.legaltext.LegalRenderer
@import dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.ComparePage
@import dev.groknull.kludgeworks.eu.legislation.viewer.content.Element
@import java.util.List
@import java.util.Map
@param DifferencesView differences
@param OutlineView outline
@param Map<Integer, SidenoteView> sidenotes
@param SidenotePieces pieces
@param Map<String, Element> neighbours
@param Map<String, SidenoteView.History> histories
@param LegalRenderer html
@param String from
@param String to
@param List<ControlsView.ViewLink> views
@param String same = ""
@param ComparePage page
<%-- Shows a comparison's Synopsis view. The controls come first. Then a ledger aligns the two
     texts by provision. The column heads name the two days: "From · the law
     on 22 March 2026".
     Each provision that differs is a row, with its occurrence and its edits in both texts. Words that it lost are
     struck through in From. Words that it gained are underlined in To. "Not in the text on this day" shows
     where a text does not have the provision. The last column says what changed it, as a sidenote
     says it (sidenotes, by number). The ↑ and ↓ links go to the difference before and after.
     The opening of the occurrence that holds the differences is a row of context.
     Before each difference come the occurrences of its parent occurrence that are the same in both texts. The
     occurrence next to the difference is a row, as the later text has it (neighbours, by eId). That row also says what last made the occurrence as it is
     (histories). The occurrences between them are in a fold. Occurrences that an act filter leaves out are
     also in a fold. In a changed table, runs of rows that are the same in both texts are folded,
     and load from the difference's own address (page) when the reader opens them. An annex
     inserted whole is a fold that loads from there too (annexfold.jte).
     The later text's outline is beside the view, with a count of differences for each occurrence. --%>
!{var total = differences.numbers().size();}
<div class="outlined">
<act-outline>
<nav class="outline counted" aria-label="Outline">
  @template.comparison.outlinehead(total = differences.numbers().size())
  @template.comparison.outlinerail(outline = outline, total = differences.numbers().size())
  @template.comparison.outline(entries = outline.entries())
</nav>
</act-outline>
<div class="differences synopsis">
  @template.comparison.sidenotepieces(pieces = pieces)
  @template.comparison.controls(differences = differences, views = views, legend = ControlsView.legend(differences, sidenotes))
  <div class="ledger legal-text legal-text-consolidated" role="table" aria-label="The two texts, aligned by provision">
    <div class="ledger-head" role="row"><div role="columnheader">Provision</div><div role="columnheader">From<span class="day">the law on ${from}</span></div><div role="columnheader">To<span class="day">the law on ${to}</span></div><div role="columnheader">What changed it</div></div>
    @for(var passage : differences.passages())
      @if(passage instanceof DifferencesView.Fold fold && fold.beside().stream().allMatch(occurrence -> neighbours.containsKey(occurrence.eId())))
        !{var between = fold.between();}
        @for(var each : fold.beside())
          @template.comparison.neighbour(occurrence = each, element = neighbours.get(each.eId()), history = histories.get(each.eId()), html = html)
          @if(between != null && each == fold.beside().getFirst())
            @template.comparison.ledgerfold(fold = between, same = same)
          @endif
        @endfor
      @elseif(passage instanceof DifferencesView.Fold fold)
        @template.comparison.ledgerfold(fold = fold, same = same)
      @elseif(passage instanceof DifferencesView.Lead lead)
        <div class="ledger-row ledger-lead" role="row"><div role="cell"></div><div class="from" role="cell">${html.element(lead.occurrence())}</div><div class="to" role="cell">${html.element(lead.occurrence())}</div><div role="cell"></div></div>
      @elseif(passage instanceof DifferencesView.Shown shown)
        !{var sidenote = sidenotes.get(shown.number());}
        !{var occurrence = html.element(shown.folded(page));}
        <div class="ledger-row difference-${shown.kind()}${sidenote != null && sidenote.attribution().corrected() ? " corrected" : ""}${sidenote != null && sidenote.prospective() != null ? " prospective" : ""}" role="row" id="difference-${shown.number()}">
          <div class="provision" role="cell">${shown.cited()}<span class="n">difference ${shown.number()} of ${total}</span></div>
          <div class="from" role="cell">@if(shown.kind().equals("inserted"))<p class="absent">Not in the text on this day</p>@else${occurrence}@endif</div>
          <div class="to" role="cell">@if(shown.kind().equals("deleted"))<p class="absent">Not in the text on this day</p>@elseif(shown.annexInserted())@template.comparison.annexfold(shown = shown, page = page)@else${occurrence}@endif</div>
          <div class="what" role="cell">
            @template.comparison.sidenote(shown = shown, sidenote = sidenote, pieces = pieces, named = false)
            <nav class="steps" aria-label="Differences">@if(shown.number() > 1)<a href="#difference-${shown.number() - 1}" aria-label="Previous difference">↑</a>@else<span aria-hidden="true">↑</span>@endif@if(shown.number() < total)<a href="#difference-${shown.number() + 1}" aria-label="Next difference">↓</a>@else<span aria-hidden="true">↓</span>@endif</nav>
          </div>
        </div>
      @endif
    @endfor
  </div>
</div>
</div>
