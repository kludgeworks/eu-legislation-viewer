@import dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.web.view.PreparingView
@param PreparingView.Progress progress
<%-- Makes one message of the preparation's stream. It holds the fragments of the page that change.
     htmx swaps each fragment in for the element with the same id (hx-partial). --%>
<hx-partial hx-target="#progress" hx-swap="outerHTML">@template.catalogue.preparing.progress(progress = progress)</hx-partial>
<hx-partial hx-target="#told" hx-swap="innerHTML">${progress.counts()}</hx-partial>
