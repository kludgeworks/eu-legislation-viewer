@import dev.groknull.kludgeworks.eu.legislation.viewer.site.Frame
@param Frame frame
@param String eli
<%-- The page of an address that no act has: the Publications Office has no act with the ELI. --%>
@template.layout.page(frame = frame, title = "No act " + eli + " · Amendment viewer", content = @`
  <div id="page" class="act-lookup">
    @template.catalogue.actlookup.missing(eli = eli)
  </div>
`)
