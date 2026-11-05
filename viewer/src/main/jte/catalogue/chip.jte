@import dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.web.view.CompareView
@param CompareView.Chip chip
<%-- An act's chip: its number, and how many differences it made here, once counted --%>
<a class="chip${chip.on() ? " on" : ""}${Integer.valueOf(0).equals(chip.count()) ? " zero" : ""}" href="${chip.href()}" title="${chip.title()}" aria-current="${chip.on() ? "true" : null}"><span class="label mono">${chip.label()}</span>@if(chip.count() != null)<span class="count">${chip.count()}</span>@endif</a>
