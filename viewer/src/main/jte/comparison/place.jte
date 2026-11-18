@import dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.ComparePage
@import dev.groknull.kludgeworks.eu.legislation.viewer.comparison.web.view.ChangesView
@import dev.groknull.kludgeworks.eu.legislation.viewer.legaltext.LegalRenderer
@import java.util.List
@param List<ChangesView.Shown> shown
@param LegalRenderer html
@param ComparePage page
@param String lead = null
<%-- Shows the differences at one place of a comparison: the wording of one change of an amending
     act. The page of an amending act loads it under the provision. Each difference shows as it
     reads, with the inserted and the deleted words marked, as in the Changes view. It has no
     sidenote, because the page says the act and the state. An annex inserted whole is a box that
     shows its size (annexfold.jte). If the place is the same in both texts, a line says so.
     A chapter that an annex keeps flat has only its heading. For it, the part shows the differences
     in its points and its attachments that the text marks with the act, and a line says so
     (lead). --%>
@if(shown.isEmpty())
  <p class="hint">The text before this act and the text with it have the same wording here.</p>
@else
  @if(lead != null)
    <p class="hint">${lead}</p>
  @endif
  <div class="book legal-text legal-text-consolidated">
    @for(var each : shown)
      <div class="place-difference difference-${each.kind()}">
        @if(each.annexInserted())
          @template.comparison.annexfold(shown = each, page = page)
        @else
          ${html.element(each.folded(page))}
        @endif
      </div>
    @endfor
  </div>
@endif
