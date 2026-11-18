@param String said
@param String read
@param String reading
<%-- Shows that the wording is the same on the two days of a comparison (said). A link opens Read
     for the later day (read), with its words (reading). --%>
<section class="same-version" role="status">
  <p>${said}</p>
  <p class="actions">
    <a class="button" href="${read}" data-view="read">${reading}</a>
  </p>
</section>
