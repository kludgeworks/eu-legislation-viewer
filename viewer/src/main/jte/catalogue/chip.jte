@import dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.web.view.CompareView
@param CompareView.Chip chip
<%-- Shows an act's chip with the act's number. After the count, the chip also shows how many
     differences the act made here. A corrigendum's chip has a dotted border. --%>
<a class="chip${chip.on() ? " on" : ""}${chip.corrigendum() ? " corrigendum" : ""}${Integer.valueOf(0).equals(chip.count()) ? " zero" : ""}" href="${chip.href()}" title="${chip.title()}" aria-current="${chip.on() ? "true" : null}"><span class="label mono">${chip.label()}</span>@if(chip.count() != null)<span class="count">${chip.count()}</span>@endif</a>
