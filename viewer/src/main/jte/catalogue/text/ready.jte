@import dev.groknull.kludgeworks.eu.legislation.viewer.content.Outline
@param String content
@param Outline outline
@param String compare
<%-- Shows the built text with its outline beside it. htmx loads the text from the content module.
     A click on an amendment mark opens that change in the act's comparisons. js/markers.js does
     this with the address in data-compare. --%>
<div id="text" class="outlined">
  <nav class="outline" aria-label="Outline">
    <p class="outline-head">Outline</p>
    @template.catalogue.outline(entries = outline.entries(), open = true)
  </nav>
  <div class="content" data-compare="${compare}" hx-get="${content}" hx-trigger="load" hx-swap="innerHTML">
    <p class="status">Loading the text…</p>
    <noscript><p><a href="${content}">The text</a></p></noscript>
  </div>
</div>
