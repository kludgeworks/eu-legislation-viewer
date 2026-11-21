@import dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.web.view.ActLookupView
@param ActLookupView lookup
@param boolean missing
<%-- Makes the message of the lookup's stream when the lookup ends without the act. It replaces the
     waiting panel, and the stream in it (hx-partial): no act has the address, or the Publications
     Office did not answer. --%>
<hx-partial hx-target="#act-lookup" hx-swap="outerHTML">@if(missing)@template.catalogue.actlookup.missing(eli = lookup.eli())@else@template.catalogue.unanswered(unanswered = lookup.unanswered())@endif</hx-partial>
