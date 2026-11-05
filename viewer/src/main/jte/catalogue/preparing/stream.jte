@import dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.web.view.PreparingView
@import dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.web.view.TimelineView
@param TimelineView timeline
@param PreparingView.Progress progress
@param java.util.List<PreparingView.Step> steps
<%-- A message of the preparation's stream: the parts of the page that change, each swapped in for
     the one by its id (htmx's hx-partial) --%>
<hx-partial hx-target="#timeline" hx-swap="outerHTML">@template.catalogue.timeline(timeline = timeline)</hx-partial>
<hx-partial hx-target="#progress" hx-swap="outerHTML">@template.catalogue.preparing.progress(progress = progress)</hx-partial>
<hx-partial hx-target="#steps" hx-swap="outerHTML">@template.catalogue.preparing.steps(steps = steps)</hx-partial>
