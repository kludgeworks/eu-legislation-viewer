@import dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.web.view.MarginView
@import dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.web.view.TagsView
@import dev.groknull.kludgeworks.eu.legislation.viewer.content.Outline
@param String content
@param Outline outline
@param TagsView tags
@param MarginView margin
@param String provision
@param java.util.List<String> provisions
@param String self
@param String eurLex
<%-- Shows the built text with its outline beside it. htmx loads the text from the content module.
     The outline follows the reader (js/outline.js): it marks the section in view and names it in
     its head. Without the script, the head names nothing.
     The tags are in the text, which the content module renders without the act. The
     hidden list holds, for each amending act, when it applies; js/tags.js reads it for each
     tag's hover. A click on a tag opens the history of its provision under the provision: the
     acts that changed it. <provision-links> has the address of that history, and lists the
     provisions that have a history and can have no tag; js/tags.js gives each of these a
     hollow sign, ▽, in the place of the tag.
     Where the text is not the law on the day, <margin-rules> lists the marks; js/margin.js puts
     them on the text when it arrives. A change that does not apply on the day gets an amber rule
     and a short line with the act. A change that the text does not show gets one grey marker,
     "Not in this text", at its place. Each act is a link to its page. The outline gets a dot on
     each part that has a mark. --%>
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
  <div class="tags" hidden>
    @for(var tag : tags.tags())
      <span data-act="${tag.act()}" data-applies="${tag.applies()}"></span>
    @endfor
  </div>
  <provision-links hidden data-href="${provision}">
    @for(var occurrence : provisions)
      <span data-occurrence="${occurrence}"></span>
    @endfor
  </provision-links>
  <margin-rules hidden>
    @for(var mark : margin.marks())
      @if(mark.marker())
        <p class="marker" data-occurrence="${mark.occurrence()}" data-end="${mark.holds()}"><span class="marker-kind">Not in this text</span>@if(!mark.words().isEmpty()): ${mark.words()}@endif · <a href="${mark.href()}">${mark.act()}</a></p>
      @else
        <p class="rule-note" data-occurrence="${mark.occurrence()}" data-heading="${mark.holds()}"><a href="${mark.href()}">${mark.act()}</a> · ${mark.words()}</p>
      @endif
    @endfor
  </margin-rules>
  <%-- If the request for the text times out or its connection fails, htmx swaps nothing.
       js/loading.js then shows the failure from the template: what failed, Try again (the page
       again), and the version on EUR-Lex. --%>
  <div class="content" hx-get="${content}" hx-trigger="load" hx-swap="innerHTML">
    <p class="status">Loading the text…</p>
    <noscript><p><a href="${content}">The text</a></p></noscript>
    <template class="failed">
      <section class="failure fault" role="alert" aria-labelledby="text-failed">
        <div class="failure-head">
          <h2 id="text-failed">The text did not load</h2>
          <p>The viewer did not send the text in time, or the connection failed.</p>
        </div>
        <div class="failure-actions">
          <a class="button" href="${self}">Try again</a>
          <a href="${eurLex}" rel="external">Read it on EUR-Lex<span aria-hidden="true"> ↗</span><span class="visually-hidden"> (opens EUR-Lex)</span></a>
        </div>
      </section>
    </template>
  </div>
</div>
