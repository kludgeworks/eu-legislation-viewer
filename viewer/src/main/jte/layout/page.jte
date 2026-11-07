@import dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.web.chrome.Chrome
@param Chrome chrome
@param String title
@param gg.jte.Content content
@param gg.jte.Content head = null
@param boolean search = true
<%-- Every page: its head, the notice, the site bar, then its content. What a page adds to its head
     (head) stays out of a boosted swap, which takes the page's content alone. The site bar has the
     way back to the reader's last search's results and a small Find an act box, but on the home
     page (search false), which has its own --%>
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
  <script src="${chrome.assets().theme()}" defer></script>
  <script src="${chrome.assets().clock()}" defer></script>
  <script src="${chrome.assets().outline()}" defer></script>
  <script src="${chrome.assets().markers()}" defer></script>
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
