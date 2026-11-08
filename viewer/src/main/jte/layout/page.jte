@import dev.groknull.kludgeworks.eu.legislation.viewer.chrome.Chrome
@param Chrome chrome
@param String title
@param gg.jte.Content content
@param gg.jte.Content head = null
@param boolean search = true
<%-- Lays out every page: its head, the notice, the site bar, then its content. A boosted swap takes
     only the page's content. Thus what a page adds to its head (head) stays out of the swap.
     The site bar has a link back to the reader's last search results and a small Find an act box.
     The home page has its own box, so it leaves out both (search false). --%>
<!DOCTYPE html>
<html lang="en" data-theme="${chrome.theme().code()}">
<head>
  <meta charset="utf-8">
  <meta name="viewport" content="width=device-width, initial-scale=1">
  <meta name="color-scheme" content="light dark">
  <title>${title}</title>
  <link rel="stylesheet" href="${chrome.assets().css()}">
  <script src="${chrome.assets().htmx()}" defer></script>
  <script src="${chrome.assets().sse()}" defer></script>
  <script type="module" src="${chrome.assets().theme()}"></script>
  <script type="module" src="${chrome.assets().clock()}"></script>
  <script type="module" src="${chrome.assets().outline()}"></script>
  <script type="module" src="${chrome.assets().markers()}"></script>
  <script type="module" src="${chrome.assets().receipt()}"></script>
  ${head}
</head>
<body>
  <div class="notice" role="note">
    <b>Unofficial.</b>
    <span>Consolidated versions, and comparisons of them, have no legal effect. Only the Official
      Journal of the European Union is authentic.</span>
  </div>
  <nav class="site" aria-label="Site">
    <a class="site-name" href="/">Amendment viewer</a>
    @if(search && chrome.results() != null)
      <a class="results" href="${chrome.results().href()}">‹ Results for “${chrome.results().words()}”</a>
    @endif
    @if(search)
      <form class="find" method="get" action="/" role="search">
        <label for="find">Find an act</label>
        <input id="find" name="q" type="search" placeholder="Title words, CELEX number or ELI">
      </form>
    @endif
    @template.layout.theme(chrome = chrome)
  </nav>
  <main>${content}</main>
</body>
</html>
