@param String content
<div id="text" hx-get="${content}" hx-trigger="load" hx-swap="outerHTML">
  <p class="status">Loading the text…</p>
  <noscript><p><a href="${content}">The text</a></p></noscript>
</div>
