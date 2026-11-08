// SPDX-License-Identifier: MIT
package dev.groknull.kludgeworks.build;

import gg.jte.ContentType;
import gg.jte.TemplateEngine;
import gg.jte.resolve.DirectoryCodeResolver;
import java.io.IOException;
import java.nio.file.Path;
import java.util.Map;

/**
 * Templates as Java sources (the macro jte_library), as jte's Maven and Gradle plugins generate
 * them: HTML, in jte's package for precompiled templates (where TemplateEngine.createPrecompiled
 * looks), with jte-models' typed facade (Templates, StaticTemplates, DynamicTemplates).
 *
 * <p>Arguments: the templates' root directory, the source jar to write, and optionally a pattern of
 * the templates to leave out of the facade, those only ever rendered by name (of their classes'
 * names, as jte-models' excludePattern matches them).
 *
 * <p>Each template must obey the {@link TemplatePolicy}.
 */
public final class JteGenerate {
  private JteGenerate() {}

  public static void main(String[] args) throws IOException {
    var jar = Path.of(args[1]);
    var directory = SourceJar.directoryFor(jar);
    var engine =
        TemplateEngine.create(
            new DirectoryCodeResolver(Path.of(args[0])),
            directory,
            ContentType.Html,
            null,
            "gg.jte.generated.precompiled");
    engine.setTrimControlStructures(true);
    engine.setHtmlPolicy(new TemplatePolicy());
    var models = args.length > 2 ? Map.of("excludePattern", args[2]) : Map.<String, String>of();
    engine.setExtensions(
        Map.of(
            "gg.jte.models.generator.ModelExtension",
            models,
            "gg.jte.jspecify.NullMarkedExtension",
            Map.of()));
    engine.generateAll();
    SourceJar.write(directory, jar);
  }
}
