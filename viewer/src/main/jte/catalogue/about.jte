@import dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.web.view.ApiResourceView
@import dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.web.view.ApiTermView
@import dev.groknull.kludgeworks.eu.legislation.viewer.site.Frame
@import java.util.List
@param Frame frame
@param String version
@param List<ApiResourceView> resources
@param List<ApiTermView> problems
<%-- Shows the About page: what the viewer is, what it keeps about a reader (Privacy), and the
     build, in the frame it shares with the API's documentation (handbook.jte). The home page's
     footer links to Privacy by its id. --%>
@template.catalogue.handbook(frame = frame, title = "About · Amendment viewer", api = false, resources = resources, problems = problems, content = @`
  <h1>About</h1>
  <section id="what">
    <h2>What this is</h2>
    <p>An unofficial reader of EU acts and their consolidated versions. It shows an act as it stood on any day,
      and what changed between two days, provision by provision, with the act that made each change and the date
      it applies from.</p>
    <p>Consolidated versions, and comparisons of them, have no legal effect. Only the Official Journal of the
      European Union is authentic. Check it, or
      <a href="https://eur-lex.europa.eu/" rel="external">EUR-Lex<span aria-hidden="true"> ↗</span><span class="visually-hidden"> (opens EUR-Lex)</span></a>,
      before relying on a provision.</p>
    <p>The texts and their dates come from the Publications Office of the European Union, which publishes them
      openly through Cellar. Other programs can read them through the <a href="/api/docs">API</a>.</p>
  </section>
  <section id="privacy">
    <h2>Privacy</h2>
    <p>The viewer sets two cookies, and only when you do something that needs them:</p>
    <ul>
      <li><b>theme</b>: the colour theme you chose (light, dark or following your device), kept for 400 days.</li>
      <li><b>search</b>: your last search, so the site bar can take you back to its results, kept for 2 hours.</li>
    </ul>
    <p>Neither identifies you, and neither is shared. There are no third-party cookies, analytics or adverts.</p>
    <p>The viewer keeps a technical record of each request: the page asked for, when, and how long the answer
      took. The record is used only to find faults and to keep the site fast.</p>
  </section>
  <section id="build">
    <h2>Build</h2>
    <p>Version: <span class="mono">${version}</span></p>
  </section>
`)
