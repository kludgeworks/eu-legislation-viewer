// SPDX-License-Identifier: MIT
package dev.groknull.kludgeworks.eu.legislation.viewer.text.infra;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.fail;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;
import javax.xml.XMLConstants;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamSource;
import javax.xml.validation.SchemaFactory;
import net.sf.saxon.s9api.DOMDestination;
import net.sf.saxon.s9api.Processor;
import net.sf.saxon.s9api.QName;
import net.sf.saxon.s9api.SaxonApiException;
import net.sf.saxon.s9api.XdmAtomicValue;
import net.sf.saxon.s9api.XdmNode;
import net.sf.saxon.s9api.XsltExecutable;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.TestFactory;
import org.xml.sax.SAXParseException;

/**
 * Normalises every fixture (raw Formex, one folder per Formex version) and checks the result: it
 * validates against legal-text.xsd, and the eIds of live units are unique. An unknown Formex
 * element fails the transformation itself.
 */
class NormaliserFixturesTest {

  static final String NS = "https://kludgeworks.groknull.dev/ns/legal-text/1";
  static final Path ROOT = Path.of(System.getenv("TEST_SRCDIR"), System.getenv("TEST_WORKSPACE"));
  static final Path XSLT =
      ROOT.resolve(
          "src/main/resources/dev/groknull/kludgeworks/eu/legislation/viewer/text/infra/xslt/normalise.xsl");
  static final Path XSD =
      ROOT.resolve(
          "src/main/resources/dev/groknull/kludgeworks/eu/legislation/viewer/text/infra/xml/legal-text.xsd");
  static final Path FIXTURES = ROOT.resolve("src/test/resources/fixtures/formex");

  final Processor saxon = new Processor(false);

  @TestFactory
  Stream<DynamicTest> fixtures() throws Exception {
    XsltExecutable normalise = saxon.newXsltCompiler().compile(new StreamSource(XSLT.toFile()));
    var schema = SchemaFactory.newInstance(XMLConstants.W3C_XML_SCHEMA_NS_URI).newSchema(XSD.toFile());
    List<Path> folders;
    try (var versions = Files.list(FIXTURES)) {
      folders =
          versions
              .filter(Files::isDirectory)
              .flatMap(NormaliserFixturesTest::children)
              .sorted()
              .toList();
    }
    return folders.stream()
        .map(
            folder ->
                DynamicTest.dynamicTest(
                    FIXTURES.relativize(folder).toString(),
                    () -> {
                      var transformer = normalise.load30();
                      // an OJ act's folder is named after its CELEX number; consolidations carry their own
                      var name = folder.getFileName().toString();
                      if (name.startsWith("3")) {
                        transformer.setStylesheetParameters(
                            java.util.Map.of(new QName("celex"), new XdmAtomicValue(name)));
                      }
                      var dom = javax.xml.parsers.DocumentBuilderFactory.newNSInstance().newDocumentBuilder().newDocument();
                      transformer.applyTemplates(new StreamSource(input(folder).toFile()), new DOMDestination(dom));
                      try {
                        schema.newValidator().validate(new DOMSource(dom));
                      } catch (SAXParseException e) {
                        fail("not valid against legal-text.xsd: " + e.getMessage());
                      }
                      XdmNode result = saxon.newDocumentBuilder().wrap(dom);
                      var xpath = saxon.newXPathCompiler();
                      xpath.declareNamespace("lt", NS);
                      var duplicates =
                          xpath
                              .evaluate(
                                  "let $ids := //(lt:unit | lt:quotedStructure | lt:note)[not(ancestor::lt:deleted)]/@eId/string()"
                                      + " return distinct-values($ids[index-of($ids, .)[2]])",
                                  result)
                              .stream()
                              .map(Object::toString)
                              .toList();
                      assertEquals(List.of(), duplicates, "eIds of live units must be unique");
                    }));
  }

  static Stream<Path> children(Path version) {
    try {
      return Files.list(version).filter(Files::isDirectory).toList().stream();
    } catch (IOException e) {
      throw new IllegalStateException(e);
    }
  }

  /** The package descriptor of an OJ act, or the single CONS.ACT file of a consolidation. */
  static Path input(Path folder) throws IOException {
    try (var files = Files.list(folder)) {
      var all = files.map(Path::getFileName).map(Path::toString).sorted().toList();
      return folder.resolve(
          all.stream()
              .filter(f -> f.endsWith(".doc.xml") || f.endsWith(".doc.fmx.xml"))
              .findFirst()
              .orElseGet(() -> all.stream().filter(f -> f.startsWith("CL")).findFirst().orElseThrow()));
    }
  }
}
