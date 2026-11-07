@import dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.web.view.SidenoteView
@param SidenoteView.Applies applies
<%-- Shows when an act applies and which clause sets that date. Example: "applies from 1 October
     2026 · Article 2 of 2026/449, as amended by 2027/12". --%>${applies.when()}@if(applies.clause() != null)${applies.between()}<a href="${applies.clause().href()}" rel="external">${applies.clause().label()}</a>@for(var each : applies.amendedBy()), as amended by <a href="${each.href()}" rel="external">${each.label()}</a>@endfor${applies.after()}@endif
