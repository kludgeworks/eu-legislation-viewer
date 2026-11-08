@import dev.groknull.kludgeworks.eu.legislation.viewer.comparison.web.view.SidenoteParts
@param SidenoteParts parts
<%-- Sends the parts that the sidenotes of the view repeat, each once (SidenoteParts). A template holds
     them, so they are not shown or found by themselves: js/sidenotes.js copies each where a sidenote
     names it by id. It comes before the differences, so a part is here when its sidenote is. --%>
<template id="sidenote-parts">@for(var each : parts.published().entrySet())<span id="${each.getKey()}">${each.getValue()}</span>@endfor@for(var each : parts.dates().entrySet())<div id="${each.getKey()}">@for(var applies : each.getValue())<span class="applies">@template.comparison.applies(applies = applies)</span>@endfor</div>@endfor</template>
