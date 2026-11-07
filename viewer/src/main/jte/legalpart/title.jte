@param String rank
@param boolean runIn
@param gg.jte.Content content
<%-- Shows a unit's title as a heading of its rank in the outline. The long title is h1. Thus rank 1
     is h2, and rank 5 and deeper is h6. A numbered unit's heading is on the same line as its number
     (runIn). --%>!{var css = runIn ? "title title-run-in" : "title";}!{var level = rank == null ? 0 : Math.min(Integer.parseInt(rank) + 1, 6);}@if(level == 2)<h2 class="${css}">${content}</h2>@elseif(level == 3)<h3 class="${css}">${content}</h3>@elseif(level == 4)<h4 class="${css}">${content}</h4>@elseif(level == 5)<h5 class="${css}">${content}</h5>@elseif(level == 6)<h6 class="${css}">${content}</h6>@else<div class="${css}">${content}</div>@endif