// SPDX-License-Identifier: MIT
package dev.groknull.kludgeworks.eu.legislation.viewer.text.infra;

import com.google.devtools.build.runfiles.AutoBazelRepository;
import com.google.devtools.build.runfiles.Runfiles;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Path;
import net.sf.saxon.s9api.Processor;
import net.sf.saxon.s9api.SaxonApiException;
import net.sf.saxon.s9api.Serializer;
import net.sf.saxon.s9api.XdmNode;

/**
 * The converter's expected output for each fixture, checked in as
 * formex-converter/test/expected/&lt;fixture&gt;.xml, so that a change to the output is a diff to
 * review: an eId that changes is a major version of the converter. Indented, which changes nothing:
 * legal-text keeps its text in attributes (t/@v), so whitespace between elements means nothing.
 * {@link UpdateExpected} writes them.
 */
@AutoBazelRepository
final class ExpectedOutput {

  private ExpectedOutput() {}

  /** The file a fixture's expected output is in, among the test's runfiles. */
  static Path of(Fixture fixture) {
    try {
      return Path.of(
          Runfiles.preload()
              .withSourceRepository(AutoBazelRepository_ExpectedOutput.NAME)
              .rlocation(
                  "kludgeworks_viewer/formex-converter/test/expected/" + fixture.name() + ".xml"));
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }

  /** A legal text as its expected output is written: UTF-8, indented, with a final newline. */
  static String serialize(Processor saxon, XdmNode legalText) throws SaxonApiException {
    var serializer = saxon.newSerializer();
    serializer.setOutputProperty(Serializer.Property.INDENT, "yes");
    serializer.setOutputProperty(Serializer.Property.ENCODING, "UTF-8");
    serializer.setOutputProperty(Serializer.Property.OMIT_XML_DECLARATION, "no");
    return serializer.serializeNodeToString(legalText) + "\n";
  }
}
