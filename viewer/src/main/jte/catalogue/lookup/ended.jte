@import dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.web.view.HomeView
@param HomeView.Unanswered unanswered
@param String none = null
<%-- Makes the message of the home page's lookup stream when the lookup ends without the act. It
     replaces the looking panel, and the stream in it (hx-partial): the line that no act has the
     CELEX number or ELI (none), or that the Publications Office did not answer. --%>
<hx-partial hx-target="#looking" hx-swap="outerHTML">@if(none != null)<p class="none" role="status">${none}</p>@else@template.catalogue.unanswered(unanswered = unanswered)@endif</hx-partial>
