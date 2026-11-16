@import dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.web.view.MarginView
@import dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.web.view.MarksView
@import dev.groknull.kludgeworks.eu.legislation.viewer.content.Outline
@param String content
@param Outline outline
@param String compare
@param MarksView marks
@param MarginView margin
<%-- Shows the built text with its outline beside it. htmx loads the text from the content module.
     The outline follows the reader (js/outline.js): it marks the section in view and names it in
     its head. Without the script, the head names nothing.
     The amendment marks are in the text, which the content module renders without the act. The
     hidden list holds, for each amending act, when it applies and the step of Compare that made its
     changes; js/markers.js reads it for each mark's hover and click. data-compare is the act's
     comparisons, for a mark whose act is not in the list.
     Where the text is not the law on the day, <margin-rules> lists the units to mark in the
     margin, and the note for a text that does not apply at all; js/margin.js puts them on the text
     when it arrives. Each mark links up to the warning in the Reading card. --%>
<div id="text" class="outlined">
  <act-outline>
  <nav class="outline" aria-label="Outline">
    <div class="outline-top">
      <p class="outline-head">Outline</p>
      <p class="outline-follow" hidden><span class="where"></span></p>
    </div>
    @template.catalogue.outline(entries = outline.entries(), open = true)
  </nav>
  </act-outline>
  <div class="marks" hidden>
    @for(var mark : marks.marks())
      <span data-act="${mark.act()}" data-applies="${mark.applies()}" data-compare="${mark.compare()}"></span>
    @endfor
  </div>
  <margin-rules hidden>
    @if(margin.note() != null)
      <p class="margin-note" role="note"><b>Does not apply on this day.</b> ${margin.note()}</p>
    @endif
    @for(var rule : margin.rules())
      <span data-unit="${rule.unit()}" data-heading="${rule.heading()}" data-label="${rule.label()}"></span>
    @endfor
  </margin-rules>
  <div class="content" data-compare="${compare}" hx-get="${content}" hx-trigger="load" hx-swap="innerHTML">
    <p class="status">Loading the text…</p>
    <noscript><p><a href="${content}">The text</a></p></noscript>
  </div>
</div>
