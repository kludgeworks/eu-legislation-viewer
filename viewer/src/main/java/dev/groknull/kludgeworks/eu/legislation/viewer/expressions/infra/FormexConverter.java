// SPDX-License-Identifier: MIT
package dev.groknull.kludgeworks.eu.legislation.viewer.expressions.infra;

import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import javax.xml.transform.stream.StreamSource;
import net.sf.saxon.lib.FeatureKeys;
import net.sf.saxon.lib.ResourceResolver;
import net.sf.saxon.s9api.Processor;
import net.sf.saxon.s9api.QName;
import net.sf.saxon.s9api.SaxonApiException;
import net.sf.saxon.s9api.XdmAtomicValue;
import net.sf.saxon.s9api.XdmDestination;
import net.sf.saxon.s9api.XdmNode;
import net.sf.saxon.s9api.XdmNodeKind;
import net.sf.saxon.s9api.XsltExecutable;
import net.sf.saxon.trans.XPathException;
import org.jspecify.annotations.Nullable;

/**
 * Turns Formex 4 into legal-text XML with {@code formex-to-legal-text.xsl}, run by its contract
 * (the stylesheet's header), its refusals turned into exceptions: the expressions module's adapter
 * on //formex-converter's stylesheets. The stylesheet is compiled once; each call gets its own
 * transformer, so one instance serves concurrent calls.
 *
 * <p>A package's files are parsed as they are read ({@link #parse}), each under a URI of its own
 * scheme, {@code formex:/{name}}: the stylesheet reads the files a package refers to with {@code
 * doc(resolve-uri(@FILE, base-uri(.)))}, which a resolver answers with the parsed file, and nothing
 * outside the package can be read that way.
 *
 * <p>Formex files can be large: an annex of 104 MB with over 100,000 entity references (&amp;amp;).
 * Since JDK 24 the JDK's XML parser stops at 100,000 ({@code jdk.xml.maxGeneralEntitySizeLimit}),
 * so the constructor lifts that limit on the processor it is given, for the documents Saxon parses.
 * Formex has no DTD, so there are no entities to expand beyond the predefined ones.
 */
final class FormexConverter {

  static final String STYLESHEET =
      "/dev/groknull/kludgeworks/formex/converter/xslt/formex-to-legal-text.xsl";
  static final String VERSION = "/dev/groknull/kludgeworks/formex/converter/version.txt";
  static final String ERRORS = "https://kludgeworks.groknull.dev/ns/formex-converter";

  /** Where a package's files are, as the stylesheet sees them: formex:/L_2009330EN.01008001.xml. */
  static final String PACKAGE = "formex:/";

  private static final QName CELEX = new QName("celex");

  private final Processor processor;
  private final XsltExecutable stylesheet;
  private final String version;

  FormexConverter(Processor processor) {
    this.processor = processor;
    processor.setConfigurationProperty(
        FeatureKeys.XML_PARSER_PROPERTY + "jdk.xml.maxGeneralEntitySizeLimit", "0");
    processor.setConfigurationProperty(
        FeatureKeys.XML_PARSER_PROPERTY + "jdk.xml.totalEntitySizeLimit", "0");
    var url = Objects.requireNonNull(FormexConverter.class.getResource(STYLESHEET), STYLESHEET);
    try {
      stylesheet = processor.newXsltCompiler().compile(new StreamSource(url.toExternalForm()));
    } catch (SaxonApiException e) {
      throw new IllegalStateException("formex-to-legal-text.xsl doesn't compile", e);
    }
    version = readVersion();
  }

  /** The converter's version (version.txt), which the stylesheet stamps on every document. */
  String version() {
    return version;
  }

  private static String readVersion() {
    try (var in = FormexConverter.class.getResourceAsStream(VERSION)) {
      return new String(Objects.requireNonNull(in, VERSION).readAllBytes(), StandardCharsets.UTF_8)
          .strip();
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }

  /**
   * One XML file of a package, parsed as it was read: its tree, or why it isn't XML (which fails a
   * conversion only if the stylesheet reads the file: a package may hold files it never reads); and
   * its size, in bytes.
   */
  record Document(
      String name, long size, @Nullable XdmNode tree, @Nullable SaxonApiException failure) {
    String uri() {
      return PACKAGE + name;
    }
  }

  /** The package's file {@code name}, parsed from {@code in} as it is read. */
  Document parse(String name, InputStream in) {
    var counted = new Counted(in);
    try {
      var tree = processor.newDocumentBuilder().build(new StreamSource(counted, PACKAGE + name));
      return new Document(name, counted.count, tree, null);
    } catch (SaxonApiException e) {
      return new Document(name, counted.count, null, e);
    }
  }

  /**
   * Converts one package's document: a consolidation's CONS.ACT or CONS.OTH file, else the package
   * descriptor (DOC), else the largest XML file; the other files are what it refers to.
   *
   * @param documents the package's XML files, parsed
   * @param celex the CELEX number of an OJ act, which Formex doesn't carry; empty for a
   *     consolidation, which carries its own
   * @return the legal-text document
   * @throws OutOfScopeException if the document isn't a legal act (notices, case law, the OJ's
   *     tables of contents)
   * @throws UnsupportedFormexException if it uses Formex the converter doesn't handle yet
   * @throws ConversionException if it can't be converted for another reason: no file of the package
   *     is XML, or one it refers to is missing or isn't XML
   */
  XdmNode convert(List<Document> documents, Optional<String> celex) {
    var document = main(documents);
    var transformer = stylesheet.load30();
    transformer.setResourceResolver(resolver(documents));
    // a refusal is an xsl:message that terminates: its text is the reason, Saxon's own message
    // isn't
    var reason = new AtomicReference<@Nullable String>();
    transformer.setMessageHandler(
        m -> reason.set(m.isTerminate() ? m.getStringValue() : reason.get()));
    var result = new XdmDestination();
    try {
      if (celex.isPresent()) {
        transformer.setStylesheetParameters(Map.of(CELEX, new XdmAtomicValue(celex.get())));
      }
      // the document is the global context item too, as Saxon's command line has it: the
      // stylesheet's global variables read it (the package's files, its amendment markers)
      transformer.setGlobalContextItem(document);
      transformer.applyTemplates(document, result);
    } catch (SaxonApiException e) {
      var refusal = reason.get();
      throw translate(e, refusal != null ? refusal : Objects.toString(e.getMessage()));
    }
    return result.getXdmNode();
  }

  private static ConversionException translate(SaxonApiException e, String message) {
    var code = e.getErrorCode();
    if (code != null && ERRORS.equals(code.getNamespaceURI())) {
      return switch (code.getLocalName()) {
        case "out-of-scope" -> new OutOfScopeException(message, e);
        case "unknown" -> new UnsupportedFormexException(message, e);
        default -> new ConversionException(message, e);
      };
    }
    return new ConversionException(message, e);
  }

  /** The document to convert, of those that are XML, in the order of their names. */
  private static XdmNode main(List<Document> documents) {
    var parsed =
        documents.stream()
            .filter(d -> d.tree() != null)
            .sorted(Comparator.comparing(Document::name))
            .toList();
    if (parsed.isEmpty()) {
      var failure = documents.stream().map(Document::failure).filter(Objects::nonNull).findFirst();
      throw failure
          .map(e -> new ConversionException("No file of the package is XML: " + e.getMessage(), e))
          .orElseGet(() -> new ConversionException("No Formex file in the package"));
    }
    var main =
        first(parsed, root -> root.startsWith("CONS."))
            .or(() -> first(parsed, "DOC"::equals))
            .orElseGet(
                () -> parsed.stream().max(Comparator.comparingLong(Document::size)).orElseThrow());
    return Objects.requireNonNull(main.tree());
  }

  private static Optional<Document> first(List<Document> documents, Predicate<String> root) {
    return documents.stream()
        .filter(d -> root.test(rootElement(Objects.requireNonNull(d.tree()))))
        .findFirst();
  }

  private static String rootElement(XdmNode document) {
    for (var child : document.children()) {
      if (child.getNodeKind() == XdmNodeKind.ELEMENT) {
        return child.getNodeName().getLocalName();
      }
    }
    return "";
  }

  /**
   * The package's files for doc(), by their formex: URI; any other URI is the stylesheet's own
   * (subdivision.rdf), which Saxon resolves as it would without a resolver.
   */
  private static ResourceResolver resolver(List<Document> documents) {
    Map<String, Document> byUri =
        documents.stream().collect(Collectors.toMap(Document::uri, Function.identity()));
    return request -> {
      if (!request.uri.startsWith(PACKAGE)) {
        return null;
      }
      var document = byUri.get(request.uri);
      if (document == null) {
        throw new XPathException(
            "The package has no file " + request.uri.substring(PACKAGE.length()));
      }
      var failure = document.failure();
      if (failure != null) {
        throw new XPathException(
            "The package's file " + document.name() + " isn't XML: " + failure.getMessage(),
            failure);
      }
      return Objects.requireNonNull(document.tree()).getUnderlyingNode();
    };
  }

  /** A stream that counts the bytes read from it. */
  private static final class Counted extends FilterInputStream {
    private long count;

    Counted(InputStream in) {
      super(in);
    }

    @Override
    public int read() throws IOException {
      var b = super.read();
      if (b >= 0) {
        count++;
      }
      return b;
    }

    @Override
    public int read(byte[] bytes, int offset, int length) throws IOException {
      var n = super.read(bytes, offset, length);
      if (n > 0) {
        count += n;
      }
      return n;
    }
  }
}
