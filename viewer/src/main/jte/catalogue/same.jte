@param String said
@param String before = null
@param String read
<%-- Where a comparison's two ends are one version (Design D-28): that they are, with its comparison
     with the version before, if there is one, and its page --%>
<section class="same-version" role="status">
  <p>${said}</p>
  <p class="actions">
    @if(before != null)<a class="button" href="${before}">Compare with the version before</a>@endif
    <a class="button" href="${read}">Read this version</a>
  </p>
</section>
