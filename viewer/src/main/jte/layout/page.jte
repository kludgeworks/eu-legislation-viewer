@import dev.groknull.kludgeworks.eu.legislation.viewer.site.Frame
@param Frame frame
@param String title
@param gg.jte.Content content
@param gg.jte.Content head = null
@param boolean search = true
<%-- Lays out every page: its head, the notice, the site bar, then its content. A boosted swap takes
     only the page's content. Thus what a page adds to its head (head) stays out of the swap.
     The site bar has a link back to the reader's last search results and a small Find an act box.
     The home page has its own box, so it leaves out both (search false). --%>
<!DOCTYPE html>
<html lang="en" data-theme="${frame.theme().code()}">
<head>
  <meta charset="utf-8">
  <meta name="viewport" content="width=device-width, initial-scale=1">
  <meta name="color-scheme" content="light dark">
  <title>${title}</title>
  <link rel="icon" href="${frame.assets().icons().svg()}" type="image/svg+xml">
  <link rel="icon" href="${frame.assets().icons().ico()}" sizes="32x32">
  <link rel="apple-touch-icon" href="${frame.assets().icons().touch()}">
  @for(var href : frame.assets().css())
    <link rel="stylesheet" href="${href}">
  @endfor
  <script src="${frame.assets().htmx()}" defer></script>
  <script src="${frame.assets().sse()}" defer></script>
  <script type="module" src="${frame.assets().theme()}"></script>
  <script type="module" src="${frame.assets().clock()}"></script>
  <script type="module" src="${frame.assets().outline()}"></script>
  <script type="module" src="${frame.assets().markers()}"></script>
  <script type="module" src="${frame.assets().margin()}"></script>
  <script type="module" src="${frame.assets().sidenotes()}"></script>
  <script type="module" src="${frame.assets().copy()}"></script>
  <script type="module" src="${frame.assets().daypicker()}"></script>
  <script type="module" src="${frame.assets().loading()}"></script>
  ${head}
</head>
<body>
  <aside class="notice" aria-label="Disclaimer" role="note">
    <b>Unofficial.</b>
    <span>Consolidated texts, and comparisons of them, have no legal effect. Only the Official
      Journal of the European Union is authentic.</span>
  </aside>
  <nav class="site" aria-label="Site">
    <a class="site-name" href="/"><img src="${frame.assets().icons().svg()}" width="20" height="20" alt="">Amendment viewer</a>
    @if(search && frame.results() != null)
      <a class="results" href="${frame.results().href()}">‹ Results for “${frame.results().words()}”</a>
    @endif
    @if(search)
      <form class="find" method="get" action="/" role="search">
        <label for="find">Find an act</label>
        <input id="find" name="q" type="search" placeholder="Title words, CELEX number or ELI">
      </form>
    @endif
    @template.layout.theme(frame = frame)
  </nav>
  <main>${content}</main>
</body>
</html>
