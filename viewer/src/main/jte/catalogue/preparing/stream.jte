@import dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.web.view.PreparingView
@import dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.web.view.TimelineView
@param TimelineView timeline
@param PreparingView.Progress progress
<%-- Makes one message of the preparation's stream. It holds the parts of the page that change.
     htmx swaps each part in for the element with the same id (hx-partial). --%>
<hx-partial hx-target="#timeline" hx-swap="outerHTML">@template.catalogue.timeline(timeline = timeline)</hx-partial>
<hx-partial hx-target="#progress" hx-swap="outerHTML">@template.catalogue.preparing.progress(progress = progress)</hx-partial>
<hx-partial hx-target="#told" hx-swap="innerHTML">${progress.counts()}</hx-partial>
