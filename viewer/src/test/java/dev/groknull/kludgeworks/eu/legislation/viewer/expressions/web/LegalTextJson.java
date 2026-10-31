// SPDX-License-Identifier: MIT
package dev.groknull.kludgeworks.eu.legislation.viewer.expressions.web;

import com.google.devtools.build.runfiles.AutoBazelRepository;
import com.google.devtools.build.runfiles.Runfiles;
import dev.groknull.kludgeworks.eu.legislation.viewer.expressions.Expression;
import dev.groknull.kludgeworks.eu.legislation.viewer.expressions.infra.LegalTextReader;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Path;
import java.util.List;
import javax.xml.transform.stream.StreamSource;
import net.sf.saxon.s9api.Processor;
import net.sf.saxon.s9api.SaxonApiException;
import org.jmolecules.jackson3.JMoleculesModule;
import tools.jackson.databind.SerializationFeature;
import tools.jackson.databind.json.JsonMapper;

/**
 * The golden JSON (URLs and API: shape drift caught by a few fixtures): the converter's expected
 * output for three texts, read into the records and written as JSON, kept in
 * viewer/src/test/resources/legal-text-json/ (mise run expected:json rewrites them). 2015/1998
 * (deep annexes, provenance), the TFEU (protocols) and 83/417 (tables).
 */
@AutoBazelRepository
final class LegalTextJson {
  static final List<String> GOLDEN =
      List.of(
          "06.02.1/02015R1998-20260322",
          "06.02.1/02016E-TXT-20250315",
          "05.56/01983L0417-20161222");

  static final String SCHEMA =
      "kludgeworks_viewer/formex-converter/src/main/resources/dev/groknull/kludgeworks/formex/converter/schema/legal-text.xsd";

  static final JsonMapper JSON =
      JsonMapper.builder()
          .addModule(new JMoleculesModule())
          .addModule(new LegalTextJsonModule())
          .enable(SerializationFeature.INDENT_OUTPUT)
          .build();

  private LegalTextJson() {}

  /** The text {@code name} (06.02.1/02015R1998-20260322) as JSON, from the converter's output. */
  static String render(String name) {
    return JSON.writeValueAsString(read(name)) + "\n";
  }

  /** The text {@code name} (06.02.1/02015R1998-20260322), read from the converter's output. */
  static Expression read(String name) {
    var saxon = new Processor(false);
    try {
      var xml =
          saxon
              .newDocumentBuilder()
              .build(
                  new StreamSource(
                      runfile(
                              "kludgeworks_viewer/formex-converter/src/test/resources/expected/formex/"
                                  + name
                                  + ".xml")
                          .toFile()));
      return new LegalTextReader(saxon).read(xml);
    } catch (SaxonApiException e) {
      throw new IllegalStateException(e);
    }
  }

  /** Where the golden JSON of {@code name} is, below {@code root}. */
  static Path golden(Path root, String name) {
    return root.resolve(name + ".json");
  }

  static Path runfile(String path) {
    try {
      return Path.of(
          Runfiles.preload()
              .withSourceRepository(AutoBazelRepository_LegalTextJson.NAME)
              .rlocation(path));
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }
}
