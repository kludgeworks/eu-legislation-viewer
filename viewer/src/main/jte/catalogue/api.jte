@import dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.web.view.ApiResourceView
@import dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.web.view.ApiTermView
@import dev.groknull.kludgeworks.eu.legislation.viewer.site.Frame
@import java.util.List
@param Frame frame
@param List<ApiResourceView> resources
@param java.util.Map<String, String> examples
@param List<ApiTermView> problems
<%-- Shows the documentation of the API for people, on one page: how the API answers, each resource
     with its members and links, and each problem type, in the frame it shares with the About page
     (handbook.jte). Each module gives its resources and problem types as HTML fragments (ApiDocs).
     The av CURIE of a HAL document links to a relation by its name, /api/docs#act, and the type of
     a problem to its problem type, /api/docs#text-failed.

     An example is a card (figure .api-example): the request, then the response's body, its headers
     and the request as curl. js/examples.js turns them into tabs, folds the repetitive parts of a
     body, and colours them; without it, the card shows all three. --%>
@template.catalogue.handbook(frame = frame, title = "API · Amendment viewer", api = true, resources = resources, problems = problems, content = @`
  <script src="${frame.assets().highlight()}" defer></script>
  <script src="${frame.assets().highlightHttp()}" defer></script>
  <script type="module" src="${frame.assets().examples()}"></script>
  <div class="api-docs">
    <h1>API</h1>
    <p class="api-lead">The API gives the text of an EU act on any day, its versions, and the changes between two
      days, provision by provision. It is free and needs no key. It starts at
      <a href="/api"><span class="mono">/api</span></a>.</p>

    <section id="conventions">
      <h2>Conventions</h2>
      <dl>
        <dt>Links</dt>
        <dd>Start at <a href="/api"><span class="mono">/api</span></a>. Each answer has links, in
          <span class="mono">_links</span>. To make the next request, follow a link. Do not make addresses yourself,
          because an address can change. The name of a link does not change. A link with
          <span class="mono">"templated" : true</span> is a template. Replace each <span class="mono">{name}</span>
          in it with a value.</dd>
        <dt>Format</dt>
        <dd>The answers are HAL, <span class="mono">application/hal+json</span>. A link name from the
          <a href="https://www.iana.org/assignments/link-relations/link-relations.xhtml" rel="external">IANA registry<span aria-hidden="true"> ↗</span><span class="visually-hidden"> (opens the IANA registry)</span></a>
          has its usual meaning, for example <span class="mono">self</span> or <span class="mono">up</span>. A link
          name that starts with <span class="mono">av:</span> is a name of this API. Its link in
          <span class="mono">curies</span> leads to its description on this page.</dd>
        <dt id="versions">Versions</dt>
        <dd>
          <p>The API has a major version. This is version 1. To ask for a version, give it in the media type:
            <span class="mono">Accept: application/hal+json;version=1</span>. A request without a version gets
            version 1. A request for a version that the API does not have gets
            <span class="mono">400 Bad Request</span>.</p>
          <p>A version can get new members and new links. So ignore the members and links that you do not know. A
            change that can break a client makes a new version. The API then also keeps the earlier version until a
            date that it announces in the header <span class="mono">Sunset</span> of its answers.</p>
        </dd>
        <dt>Schemas</dt>
        <dd>An answer for a <a href="#text">text</a>, a <a href="#unit">unit</a> or a
          <a href="#difference">difference</a> links to its JSON Schema, <span class="mono">describedby</span>. The
          schemas of version 1 are in <span class="mono">/api/schemas/v1/</span>. A schema lets an answer have
          members that it does not name.</dd>
        <dt id="waiting">Waiting</dt>
        <dd>
          <p>The viewer makes a text the first time that someone asks for it. Until the text is ready, the answer is
            <span class="mono">202 Accepted</span>. The answer has <span class="mono">Retry-After</span>, in
            seconds, and a link to the <a href="#preparation">preparation</a>.</p>
          <p>To wait without asking again, follow the link <a class="mono" href="#events">av:events</a> of the
            preparation. It is a stream of events. The last event is <span class="mono">settled</span>:</p>
$unsafe{examples.get("act/events")}
        </dd>
        <dt>Errors</dt>
        <dd>An error is a problem, <span class="mono">application/problem+json</span>. Its status tells what went
          wrong. If the status is not enough, the problem also has a <span class="mono">type</span>. The types are
          in <a href="#problems">Problem types</a>.</dd>
        <dt>Caching</dt>
        <dd>Each answer has an <span class="mono">ETag</span>. Send it back in
          <span class="mono">If-None-Match</span>. If the answer did not change, the status is
          <span class="mono">304 Not Modified</span>.</dd>
        <dt>Stability</dt>
        <dd>The API is in development. Until its first release, version 1 can change without notice.</dd>
      </dl>
    </section>

    <section id="resources">
      <h2>Resources</h2>
      <p>The examples are real answers, for the act 2015/1998.</p>
      @for(var resource : resources)
        <section id="${resource.id()}">
          <h3>${resource.title()}</h3>
          $unsafe{resource.html()}
        </section>
      @endfor
    </section>

    <section id="problems">
      <h2>Problem types</h2>
      @for(var problem : problems)
        <section id="${problem.name()}">
          <h3 class="mono">${problem.name()}</h3>
          $unsafe{problem.description()}
        </section>
      @endfor
    </section>
  </div>
`)
