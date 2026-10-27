// SPDX-License-Identifier: MIT
package dev.groknull.kludgeworks.eu.legislation.viewer.text.infra;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.fail;

import dev.groknull.kludgeworks.eu.legislation.viewer.text.infra.xml.LegalText;
import jakarta.xml.bind.JAXBContext;
import jakarta.xml.bind.JAXBElement;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamSource;
import javax.xml.validation.Schema;
import javax.xml.validation.SchemaFactory;
import net.sf.saxon.s9api.DOMDestination;
import net.sf.saxon.s9api.NullDestination;
import net.sf.saxon.s9api.Processor;
import net.sf.saxon.s9api.QName;
import net.sf.saxon.s9api.SaxonApiException;
import net.sf.saxon.s9api.XdmAtomicValue;
import net.sf.saxon.s9api.XsltExecutable;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.TestFactory;
import org.junit.jupiter.api.TestInstance;
import org.xml.sax.SAXParseException;

/**
 * Normalises every fixture: raw Formex as Cellar serves it, one folder per document under the
 * Formex version it uses (fixtures/formex/&lt;version&gt;/&lt;CELEX&gt;), covering every Formex 4 version
 * and every document type in scope. Each result validates against legal-text.xsd, unmarshals into
 * the classes generated from it, and has unique eIds among live units. Documents out of scope
 * (fixtures/refused) must be refused, not half-converted.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class NormaliserFixturesTest {

  static final String NS = "https://kludgeworks.groknull.dev/ns/legal-text/1";
  static final Path ROOT = Path.of(System.getenv("TEST_SRCDIR"), System.getenv("TEST_WORKSPACE"));
  static final Path XSLT =
      ROOT.resolve(
          "src/main/resources/dev/groknull/kludgeworks/eu/legislation/viewer/text/infra/xslt/normalise.xsl");
  static final Path XSD =
      ROOT.resolve(
          "src/main/resources/dev/groknull/kludgeworks/eu/legislation/viewer/text/infra/xml/legal-text.xsd");
  static final Path FIXTURES = ROOT.resolve("src/test/resources/fixtures");
  static final Pattern ROOT_ELEMENT = Pattern.compile("<([A-Z][A-Z0-9.]*)[\\s>/]");

  final Processor saxon = new Processor(false);
  XsltExecutable normalise;
  Schema schema;
  JAXBContext jaxb;

  @BeforeAll
  void compile() throws Exception {
    normalise = saxon.newXsltCompiler().compile(new StreamSource(XSLT.toFile()));
    schema = SchemaFactory.newInstance(XMLConstants.W3C_XML_SCHEMA_NS_URI).newSchema(XSD.toFile());
    jaxb = JAXBContext.newInstance(LegalText.class.getPackageName(), LegalText.class.getClassLoader());
  }

  @TestFactory
  Stream<DynamicTest> normalises() throws IOException {
    return folders(FIXTURES.resolve("formex"), 2).stream()
        .map(folder -> DynamicTest.dynamicTest(name(folder), () -> check(folder)));
  }

  @TestFactory
  Stream<DynamicTest> refuses() throws IOException {
    return folders(FIXTURES.resolve("refused"), 1).stream()
        .map(
            folder ->
                DynamicTest.dynamicTest(
                    name(folder),
                    () -> {
                      var e =
                          assertThrows(
                              SaxonApiException.class,
                              () ->
                                  normalise
                                      .load30()
                                      .applyTemplates(
                                          new StreamSource(input(folder).toFile()), new NullDestination()));
                      assertEquals("out-of-scope", e.getErrorCode().getLocalName(), e.getMessage());
                    }));
  }

  void check(Path folder) throws Exception {
    var transformer = normalise.load30();
    // consolidations carry their CELEX; everything else is named after it
    var name = folder.getFileName().toString();
    if (!name.startsWith("0")) {
      transformer.setStylesheetParameters(Map.of(new QName("celex"), new XdmAtomicValue(name)));
    }
    var dom = DocumentBuilderFactory.newNSInstance().newDocumentBuilder().newDocument();
    transformer.applyTemplates(new StreamSource(input(folder).toFile()), new DOMDestination(dom));
    try {
      schema.newValidator().validate(new DOMSource(dom));
    } catch (SAXParseException e) {
      fail("not valid against legal-text.xsd: " + e.getMessage());
    }
    var unmarshalled = (JAXBElement<?>) jaxb.createUnmarshaller().unmarshal(dom);
    assertEquals(
        LegalText.class, unmarshalled.getValue().getClass(), "unmarshals into the generated classes");
    var xpath = saxon.newXPathCompiler();
    xpath.declareNamespace("lt", NS);
    var duplicates =
        xpath
            .evaluate(
                "let $ids := //(lt:unit | lt:quotedStructure | lt:note)[not(ancestor::lt:deleted)]/@eId/string()"
                    + " return distinct-values($ids[index-of($ids, .)[2]])",
                saxon.newDocumentBuilder().wrap(dom))
            .stream()
            .map(Object::toString)
            .toList();
    assertEquals(List.of(), duplicates, "eIds of live units must be unique");
  }

  static String name(Path folder) {
    return FIXTURES.relativize(folder).toString();
  }

  static List<Path> folders(Path base, int depth) throws IOException {
    if (!Files.isDirectory(base)) {
      return List.of();
    }
    try (var paths = Files.walk(base, depth)) {
      return paths
          .filter(p -> !p.equals(base) && base.relativize(p).getNameCount() == depth && Files.isDirectory(p))
          .sorted()
          .toList();
    }
  }

  /**
   * The file to normalise: a consolidation's CONS.ACT or CONS.OTH file, else the package descriptor
   * (DOC), else the largest file.
   */
  static Path input(Path folder) throws IOException {
    List<Path> files;
    try (var list = Files.list(folder)) {
      files = list.filter(p -> p.toString().endsWith(".xml")).sorted().toList();
    }
    Map<Path, String> roots = files.stream().collect(Collectors.toMap(p -> p, NormaliserFixturesTest::rootElement));
    return files.stream()
        .filter(p -> roots.get(p).startsWith("CONS."))
        .findFirst()
        .or(() -> files.stream().filter(p -> roots.get(p).equals("DOC")).findFirst())
        .orElseGet(
            () -> files.stream().max(Comparator.comparingLong(NormaliserFixturesTest::size)).orElseThrow());
  }

  static String rootElement(Path file) {
    try (InputStream in = Files.newInputStream(file)) {
      var head = new String(in.readNBytes(4096), StandardCharsets.UTF_8);
      var m = ROOT_ELEMENT.matcher(head);
      return m.find() ? m.group(1) : "";
    } catch (IOException e) {
      throw new IllegalStateException(e);
    }
  }

  static long size(Path file) {
    try {
      return Files.size(file);
    } catch (IOException e) {
      throw new IllegalStateException(e);
    }
  }
}
