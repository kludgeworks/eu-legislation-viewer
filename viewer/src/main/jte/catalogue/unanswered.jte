@import dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.web.view.HomeView
@param HomeView.Unanswered unanswered
<%-- Says that the lookup of an act did not finish: the Publications Office did not answer in time,
     or the connection failed. Try again searches again, which looks the act up again. The act is
     also on EUR-Lex. The home page shows it when the lookup fails, and keeps it in a template
     while the lookup runs, for js/loading.js to show if the request times out. --%>
<section class="failure fault" role="alert" aria-labelledby="lookup-failed">
  <div class="failure-head">
    <h2 id="lookup-failed">The lookup did not finish</h2>
    <p>The Publications Office did not answer in time, or the connection failed.</p>
  </div>
  <div class="failure-actions">
    <a class="button" href="${unanswered.again()}">Try again</a>
    <a href="${unanswered.eurLex()}" rel="external">Read it on EUR-Lex<span aria-hidden="true"> ↗</span><span class="visually-hidden"> (opens EUR-Lex)</span></a>
  </div>
</section>
