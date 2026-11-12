<%-- The shapes of the views of an act's page, and the status line of its wait. js/loading.js puts
     the shape of the view that is coming over the text while the next page loads: text lines with
     sidenote blocks (Changes, Whole text), table rows (Synopsis), plain lines (Read). CSS shows it
     only after a second, so a quick page never shows it. --%>
<p class="page-status visually-hidden" role="status" aria-live="polite"></p>
<template id="skeleton-read"><div class="skeleton-over" aria-hidden="true"><div class="skeleton"><i></i><i></i><i></i><i></i><i></i><i></i><i></i><i></i></div></div></template>
<template id="skeleton-changes"><div class="skeleton-over" aria-hidden="true"><div class="skeleton noted"><i></i><b></b><i></i><i></i><b></b><i></i><i></i><b></b></div></div></template>
<template id="skeleton-whole"><div class="skeleton-over" aria-hidden="true"><div class="skeleton noted"><i></i><i></i><b></b><i></i><i></i><i></i><b></b><i></i></div></div></template>
<template id="skeleton-synopsis"><div class="skeleton-over" aria-hidden="true"><div class="skeleton rows"><i></i><i></i><i></i><i></i><i></i><i></i><i></i><i></i></div></div></template>
