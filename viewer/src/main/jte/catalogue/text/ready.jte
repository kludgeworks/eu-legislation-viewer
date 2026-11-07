@import dev.groknull.kludgeworks.eu.legislation.viewer.content.Outline
@param String content
@param Outline outline
@param String compare
<%-- The text, built: its outline beside it, the text itself loaded from the content module; its
     amendment marks open the change in the act's comparisons (compare, js/markers.js) --%>
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
