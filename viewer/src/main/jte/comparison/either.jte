@import dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.ComparePage
@import dev.groknull.kludgeworks.eu.legislation.viewer.comparison.web.view.ChangesView
@import dev.groknull.kludgeworks.eu.legislation.viewer.legaltext.LegalRenderer
@import java.util.List
@param String heading
@param List<ChangesView.Shown> shown
@param boolean open
@param LegalRenderer html
@param ComparePage page
<%-- Shows the differences that the act of a page or another act of its version made, where the
     data does not say which. The page of an amending act loads it under its own changes. The
     heading names the acts: "By this act or by 2019/413". Each difference has the name of its
     provision, what happened to it, and its wording with the inserted and the deleted words
     marked. The rule beside the list is grey and broken, as for all that the data does not tell.
     The wording is in a fold. The folds are open if the list has few changes, as on the page
     that loads the part. If there is no such difference, the part is empty and stays hidden. --%>
@if(shown.isEmpty())
  <div class="either" hidden></div>
@else
  <section class="either run open" aria-labelledby="either-head">
    <h2 id="either-head">${heading}</h2>
    <p class="note">The data does not say which of these acts made these changes. <span class="size">${shown.size() == 1 ? "1 provision" : shown.size() + " provisions"}</span></p>
    <ul class="changes-list worded">
      @for(var each : shown)
        <li>
          <details class="wording" open="${open}">
            <summary><span class="place">${each.place()}</span> <span class="made">${each.made()}</span><span class="show"> · <u>wording</u></span></summary>
            <div class="wording-text book legal-text legal-text-consolidated">
              <div class="place-difference difference-${each.kind()}">
                @if(each.annexInserted())
                  @template.comparison.annexfold(shown = each, page = page)
                @else
                  ${html.element(each.folded(page))}
                @endif
              </div>
            </div>
          </details>
        </li>
      @endfor
    </ul>
  </section>
@endif
