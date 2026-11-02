@param String title
@param gg.jte.Content content
<!DOCTYPE html>
<html lang="en">
<head>
  <meta charset="utf-8">
  <meta name="viewport" content="width=device-width, initial-scale=1">
  <title>${title}</title>
  <link rel="stylesheet" href="/css/viewer.css">
  <script src="/webjars/htmx.org/dist/htmx.min.js" defer></script>
</head>
<body>
  <header class="site">
    <a class="site-name" href="/">Amendment viewer</a>
    <p class="notice">Unofficial. Only the Official Journal of the European Union is authentic.</p>
  </header>
  <main>${content}</main>
</body>
</html>
