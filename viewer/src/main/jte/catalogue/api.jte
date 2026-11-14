@import dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.web.view.ApiStepView
@import dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.web.view.ApiTermView
@import dev.groknull.kludgeworks.eu.legislation.viewer.chrome.Chrome
@import java.util.List
@param Chrome chrome
@param List<ApiStepView> guide
@param java.util.Map<String, String> examples
@param List<ApiTermView> relations
@param List<ApiTermView> problems
<%-- Shows the documentation of the API for people, on one page: a walk from the root to a
     comparison, how the API answers, and what each link relation and problem type means. Each
     module gives its steps of the walk, as HTML fragments, and its relations and problem types
     (ApiDocsController). The av CURIE of a HAL document links to a relation by its name,
     /api/docs#act, and the type of a problem to its problem type, /api/docs#text-failed.

     A column of contents beside the page, as a handbook has. An example is a card (figure
     .api-example): the request, then the response's body, its headers and the request as curl.
     js/examples.js turns them into tabs, folds the repetitive parts of a body, and colours them;
     without it, the card shows all three. --%>
@template.layout.page(chrome = chrome, title = "API · Amendment viewer", content = @`
  <script src="${chrome.assets().highlight()}" defer></script>
  <script src="${chrome.assets().highlightHttp()}" defer></script>
  <script type="module" src="${chrome.assets().examples()}"></script>
  <div class="api-docs">
    <nav class="api-contents" aria-label="Contents">
      <h2>About</h2>
      <ul>
        <li><a href="/about#what">What this is</a></li>
        <li><a href="/about#privacy">Privacy</a></li>
        <li><a href="/about#build">Build</a></li>
      </ul>
      <h2>API</h2>
      <ul>
        <li><a href="#start">Start here</a>
          <ul>
            @for(var step : guide)
              <li><a href="#${step.id()}">${step.title()}</a></li>
            @endfor
          </ul>
        </li>
        <li><a href="#answers">How the API answers</a></li>
        <li><a href="#text">The text</a></li>
        <li><a href="#relations">Link relations</a>
          <ul class="api-terms">
            @for(var relation : relations)
              <li><a class="mono" href="#${relation.name()}">av:${relation.name()}</a></li>
            @endfor
          </ul>
        </li>
        <li><a href="#problems">Problem types</a>
          <ul class="api-terms">
            @for(var problem : problems)
              <li><a class="mono" href="#${problem.name()}">${problem.name()}</a></li>
            @endfor
          </ul>
        </li>
      </ul>
    </nav>

    <article>
      <h1>API</h1>
      <p class="api-lead">The API gives the text of an EU act on any day, its versions, and the changes between two
        days, provision by provision. It is free and needs no key. It starts at
        <a href="/api"><span class="mono">/api</span></a>.</p>

      <section id="start">
        <h2>Start here</h2>
        <p>Each answer has links, in <span class="mono">_links</span>. To make the next request, follow a link. The
          examples are real answers, for the act 2015/1998.</p>

        <ol class="api-guide">
          @for(var step : guide)
            <li id="${step.id()}">
              <h3>${step.title()}</h3>
              $unsafe{step.html()}
            </li>
          @endfor
        </ol>
      </section>

      <section id="answers">
        <h2>How the API answers</h2>
        <dl>
          <dt>Format</dt>
          <dd>The answers are HAL, <span class="mono">application/hal+json</span>. A link name from the
            <a href="https://www.iana.org/assignments/link-relations/link-relations.xhtml" rel="external">IANA registry<span aria-hidden="true"> ↗</span><span class="visually-hidden"> (opens the IANA registry)</span></a>
            has its usual meaning, for example <span class="mono">self</span> or <span class="mono">up</span>. A link
            name that starts with <span class="mono">av:</span> is one of the <a href="#relations">link relations</a>
            below.</dd>
          <dt>Links</dt>
          <dd>Follow the links. Do not make addresses yourself, because an address can change. The name of a link does
            not change. A link with <span class="mono">"templated" : true</span> is a template. Replace each
            <span class="mono">{name}</span> in it with a value.</dd>
          <dt id="answers-waiting">Waiting</dt>
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
          <dd>The API is in development. It can change without notice.</dd>
        </dl>
      </section>

      <section id="text">
        <h2>The text</h2>
        <p>A text is in parts, as the Official Journal prints it: <span class="mono">title</span>,
          <span class="mono">preamble</span>, <span class="mono">body</span>, <span class="mono">conclusions</span>,
          <span class="mono">annexes</span> and <span class="mono">notes</span>. A part that the text does not have is
          not in the answer. Its
          <a href="/api/schema/text.json">JSON Schema</a> gives each member.</p>
        <p>A part is a list of blocks. The <span class="mono">type</span> of a block tells what it is:</p>
        <dl class="api-members">
          <dt>text</dt>
          <dd>A paragraph of running text.</dd>
          <dt>unit</dt>
          <dd>An article, a paragraph, a point, an annex and so on. It has an <span class="mono">eId</span>, a
            <span class="mono">kind</span>, and its blocks in <span class="mono">content</span>. The list of kinds can
            grow.</dd>
          <dt>table</dt>
          <dd>A table, in <span class="mono">rows</span> of <span class="mono">cells</span>. A cell holds blocks, because
            a cell can hold units.</dd>
          <dt>figure</dt>
          <dd>An image, by the name of its file.</dd>
          <dt>quotation</dt>
          <dd>Text that an amending act quotes. It is not law of the act that quotes it.</dd>
          <dt>deleted</dt>
          <dd>Blocks that an amendment deleted, which the consolidated version still shows. They are not in force.</dd>
        </dl>
        <p>Running text has two forms. <span class="mono">text</span> is plain text. It does not have the markers of
          notes or the words that an amendment deleted. <span class="mono">html</span> is HTML with only these
          elements:</p>
        <ul>
          <li><span class="mono">b</span>, <span class="mono">i</span>, <span class="mono">u</span>,
            <span class="mono">s</span>, <span class="mono">sup</span>, <span class="mono">sub</span>, and
            <span class="mono">span</span> with a <span class="mono">class</span>, for typefaces.</li>
          <li><span class="mono">time</span>, with <span class="mono">datetime</span>, for a date.</li>
          <li><span class="mono">cite</span> for a reference to the Official Journal, and <span class="mono">a</span>
            for a link. The marker of a note links to <span class="mono">#</span> and the note's
            <span class="mono">eId</span>.</li>
          <li><span class="mono">span</span> with <span class="mono">data-act</span> and
            <span class="mono">data-action</span> for words that an amending act changed, and
            <span class="mono">s</span> with them for words that it deleted.</li>
          <li><span class="mono">ins</span> and <span class="mono">del</span> for the words that a comparison
            marks.</li>
          <li><span class="mono">br</span>, <span class="mono">span</span> with <span class="mono">lang</span>,
            <span class="mono">img</span>, and <span class="mono">math</span> in MathML.</li>
        </ul>
        <p>A block that a consolidation changed has <span class="mono">amendedBy</span>: the amending act, by its
          CELEX number, and what it did.</p>
      </section>

      <section id="relations">
        <h2>Link relations</h2>
        @for(var relation : relations)
          <section id="${relation.name()}">
            <h3 class="mono">av:${relation.name()}</h3>
            $unsafe{relation.description()}
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
    </article>
  </div>
`)
