// SPDX-License-Identifier: MIT
package dev.groknull.kludgeworks.eu.legislation.legaltext;

import dev.groknull.kludgeworks.eu.legislation.viewer.content.model.Decoder;
import dev.groknull.kludgeworks.eu.legislation.viewer.content.model.Encoder;
import dev.groknull.kludgeworks.eu.legislation.viewer.content.model.LegalTexts;
import gg.jte.ContentType;
import gg.jte.TemplateEngine;
import gg.jte.output.StringOutput;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Renders a folder of legal texts as the viewer shows them. The texts are the {@code {CELEX}.xml}
 * files that {@code convert_texts} writes. Each text becomes a page of its own at {@code
 * {out}/{CELEX}/{language}/index.html}.
 *
 * <p>Each text is stored and read back with {@link Encoder} and {@link Decoder}, and rendered by
 * its templates. The page frame comes from {@code layout/page.jte} and {@code
 * catalogue/version.jte}. The stylesheet is at {@code /css/viewer.css}. {@code text:survey}
 * compares these pages with EUR-Lex. No database or Cellar is necessary.
 *
 * <pre>
 * bazel run …:render_texts -- {folder} {out} {language}
 * </pre>
 */
public final class RenderTexts {

  private RenderTexts() {}

  public static void main(String[] args) throws IOException {
    if (args.length != 3) {
      throw new IllegalArgumentException("usage: render_texts {folder} {out} {language}");
    }
    var html = new LegalRenderer(TemplateEngine.createPrecompiled(ContentType.Html));
    var out = Path.of(args[1]);
    var rendered = 0;
    try (var files = Files.list(Path.of(args[0]))) {
      for (var file : files.filter(f -> f.toString().endsWith(".xml")).sorted().toList()) {
        var celex = file.getFileName().toString().replaceFirst("\\.xml$", "");
        var text = Decoder.element(Encoder.encode(LegalTexts.parse(file)));
        var body = new StringOutput();
        html.element(text).writeTo(body);
        var page = Files.createDirectories(out.resolve(celex).resolve(args[2]));
        Files.writeString(
            page.resolve("index.html"), page(celex, body.toString()), StandardCharsets.UTF_8);
        rendered++;
      }
    }
    System.out.println(rendered + " pages in " + out);
  }

  private static String page(String celex, String text) {
    return """
    <!DOCTYPE html>
    <html lang="en">
    <head>
      <meta charset="utf-8">
      <meta name="viewport" content="width=device-width, initial-scale=1">
      <title>%1$s</title>
      <link rel="stylesheet" href="/css/viewer.css">
    </head>
    <body>
      <header class="site"><a class="site-name" href="/">Amendment viewer</a></header>
      <main>
        <header class="version"><p class="version-celex">%1$s</p></header>
        %2$s
      </main>
    </body>
    </html>
    """
        .formatted(celex, text);
  }
}
