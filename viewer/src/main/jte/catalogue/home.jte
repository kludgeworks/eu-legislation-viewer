@import dev.groknull.kludgeworks.eu.legislation.viewer.chrome.Chrome
@import dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.web.view.HomeView
@param Chrome chrome
@param String title
@param HomeView home
<%-- Shows the home page. Its large line says what the viewer does (the site bar has the name), then
     comes the Find an act box. Then come a search's results. Each result shows the act's CELEX and kind, its title,
     its link and the pages it goes to. Filters by kind of act and links to the pages before and
     after go with the results. At the end, three notes say what this is, what it adds and what it
     is not, and a footer links to the About page, its Privacy and its API. The page leaves out the small search box of the site bar (search false).
     For a CELEX number or ELI of an act that the catalogue does not have yet, the page says at once
     that it looks the act up. htmx then asks the lookup address, which goes to the act when the
     Publications Office answers, or comes back with the line that no act has it. Without
     JavaScript, the head sends the browser to the lookup address. --%>
@template.layout.page(chrome = chrome, title = title, search = false, head = @`@if(home.lookingUp() != null)<noscript><meta http-equiv="refresh" content="0;url=${home.lookingUp().href()}"></noscript>@endif`, content = @`
  <div class="home">
    <header class="home-mast">
      <h1 class="home-name">Read EU legislation as it stood on any day, and see what changed</h1>
    </header>
    <form class="find-act" method="get" action="/" role="search">
      <label for="q">Find an act</label>
      <div class="field"><input id="q" name="q" type="search" value="${home.words()}" placeholder="Title words, CELEX number or ELI" autofocus="${home.words().isEmpty()}"><button type="submit">Search</button></div>
      <p class="ex">A CELEX number or ELI goes straight to the act. Try <a href="/?q=32015R1998">32015R1998</a> <a href="/?q=eli/reg_impl/2015/1998/oj">eli/reg_impl/2015/1998/oj</a> <a href="/?q=air+cargo+security">air cargo security</a></p>
    </form>
    @if(home.lookingUp() != null)
      <p class="none looking" role="status" hx-get="${home.lookingUp().href()}" hx-trigger="load" hx-target="main" hx-select="main" hx-swap="outerHTML">${home.lookingUp().line()}</p>
    @endif
    @if(home.none() != null)
      <p class="none" role="status">${home.none()}</p>
    @endif
    @if(home.results() != null)
      !{var results = home.results();}
      <section class="results" aria-labelledby="results-head">
        <div class="results-head">
          <h2 id="results-head">Acts matching “${results.words()}”</h2>
          <nav class="filters" aria-label="Type of act">
            @for(var filter : results.filters())
              <a href="${filter.href()}" aria-current="${filter.on() ? "page" : null}">${filter.label()}</a>
            @endfor
          </nav>
        </div>
        @if(results.rows().isEmpty())
          <p class="none">No act of this kind has all these words in its title.</p>
        @endif
        @for(var row : results.rows())
          <article class="result">
            <header class="cx">${row.celex()}<span class="k">${row.kind()}</span></header>
            <div class="t">
              @if(row.href() != null)<a href="${row.href()}">${row.head()}</a>@else<span class="head">${row.head()}</span>@endif
              @if(row.rest() != null)<p>${row.rest()}</p>@endif
              @for(var go : row.goes())
                <a class="to" href="${go.href()}">${go.label()} →</a>
              @endfor
            </div>
          </article>
        @endfor
        @if(!results.rows().isEmpty() || results.previous() != null)
          <div class="more">
            <span>@if(results.rows().isEmpty())No more acts@else Acts ${results.first()}–${results.last()}, closest match first@endif</span>
            <span class="pages">
              @if(results.previous() != null)<a href="${results.previous()}">‹ Previous ${results.size()}</a>@endif
              @if(results.next() != null)<a href="${results.next()}">Next ${results.size()} ›</a>@endif
            </span>
          </div>
        @endif
      </section>
    @endif
    <section class="about" aria-label="About this viewer">
      <div><h3>What this is</h3><p>An unofficial reader for EU acts and their consolidated versions, converted from the Publications Office’s Formex files.</p></div>
      <div><h3>What it adds</h3><p>Pick any two days and see what differs between the law on each, provision by provision, with the amending act, its Official Journal reference and the date each change applies from.</p></div>
      <div><h3>What it isn’t</h3><p>Not the authentic text. Check the Official Journal, or EUR-Lex, before relying on a provision.</p></div>
    </section>
    <footer class="home-foot"><a href="/about">About</a> · <a href="/about#privacy">Privacy</a> · <a href="/about#api">API</a></footer>
  </div>
`)
