@import dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.web.chrome.Chrome
@import dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.web.view.HomeView
@param Chrome chrome
@param String title
@param HomeView home
<%-- The home page (Design: Home page): the viewer's name and what it is, the Find an act box, then
     a search's results: each act's CELEX and kind, its title, the link, and where it goes; filters
     by kind of act, the pages before and after. Then three notes: what this is, what it adds, what
     it isn't. The site bar's own small box is left out (search false) --%>
@template.layout.page(chrome = chrome, title = title, search = false, content = @`
  <div class="home">
    <header class="home-mast">
      <p class="home-name">Amendment viewer</p>
      <p>EU legislation as it read on any version, and exactly what changed between two versions, act by act.</p>
    </header>
    <form class="find-act" method="get" action="/" role="search">
      <label for="q">Find an act</label>
      <div class="field"><input id="q" name="q" type="search" value="${home.words()}" placeholder="Title words, CELEX number or ELI" autofocus="${home.words().isEmpty()}"><button type="submit">Search</button></div>
      <p class="ex">A CELEX number or ELI goes straight to the act. Try <a href="/?q=32015R1998">32015R1998</a> <a href="/?q=eli/reg_impl/2015/1998/oj">eli/reg_impl/2015/1998/oj</a> <a href="/?q=air+cargo+security">air cargo security</a></p>
    </form>
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
          <div class="result">
            <div class="cx">${row.celex()}<span class="k">${row.kind()}</span></div>
            <div class="t">
              @if(row.href() != null)<a href="${row.href()}">${row.head()}</a>@else<span class="head">${row.head()}</span>@endif
              @if(row.rest() != null)<p>${row.rest()}</p>@endif
              @for(var go : row.goes())
                <a class="to" href="${go.href()}">${go.label()} →</a>
              @endfor
            </div>
          </div>
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
      <div><h3>What it adds</h3><p>Pick any two versions of an act and see what differs, provision by provision, with the amending act, its Official Journal reference and the date each change applies from.</p></div>
      <div><h3>What it isn’t</h3><p>Not the authentic text. Check the Official Journal, or EUR-Lex, before relying on a provision.</p></div>
    </section>
  </div>
`)
