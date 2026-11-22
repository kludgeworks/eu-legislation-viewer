@import dev.groknull.kludgeworks.eu.legislation.viewer.comparison.web.view.SidenotePieces
@param SidenotePieces pieces
<%-- Sends the pieces that the sidenotes of the view repeat, each once (SidenotePieces). A template holds
     them, so they are not shown or found by themselves: js/sidenotes.js copies each where a sidenote
     names it by id. It comes before the differences, so a piece is here when its sidenote is. --%>
<template id="sidenote-pieces">@for(var each : pieces.published().entrySet())<span id="${each.getKey()}">${each.getValue()}</span>@endfor@for(var each : pieces.dates().entrySet())<div id="${each.getKey()}">@for(var applies : each.getValue())<span class="applies">@template.comparison.applies(applies = applies)</span>@endfor</div>@endfor</template>
