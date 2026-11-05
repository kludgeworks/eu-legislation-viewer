@import dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.web.chrome.Chrome
@param Chrome chrome
@param String title
@param gg.jte.Content content
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
</head>
<body>
  <div class="notice" role="note">
    <b>Unofficial.</b>
    <span>Consolidated versions, and comparisons of them, have no legal effect. Only the Official
      Journal of the European Union is authentic.</span>
  </div>
  <nav class="site" aria-label="Site">
    <a class="site-name" href="/">Amendment viewer</a>
    @template.layout.theme(chrome = chrome)
  </nav>
  <main>${content}</main>
</body>
</html>
