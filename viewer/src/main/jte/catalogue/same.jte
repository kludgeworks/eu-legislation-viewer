@param String said
@param String before = null
@param String read
<%-- Shows that a comparison's two ends are the same version. It links to that version's page. If
     there is a version before it, it also links to the comparison with that version. --%>
<section class="same-version" role="status">
  <p>${said}</p>
  <p class="actions">
    @if(before != null)<a class="button" href="${before}">Compare with the version before</a>@endif
    <a class="button" href="${read}">Read this version</a>
  </p>
</section>
