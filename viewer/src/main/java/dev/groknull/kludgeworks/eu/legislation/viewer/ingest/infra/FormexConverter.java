// SPDX-License-Identifier: MIT
package dev.groknull.kludgeworks.eu.legislation.viewer.ingest.infra;

import dev.groknull.kludgeworks.eu.legislation.viewer.ingest.model.ConversionException;
import dev.groknull.kludgeworks.eu.legislation.viewer.ingest.model.MisfiledFormexException;
import dev.groknull.kludgeworks.eu.legislation.viewer.ingest.model.OutOfScopeException;
import dev.groknull.kludgeworks.eu.legislation.viewer.ingest.model.UnsupportedFormexException;
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
 * Turns Formex 4 into legal-text XML with {@code formex-to-legal-text.xsl}. It is the adapter of
 * the ingest module on the stylesheets of //formex-converter. It runs the stylesheet by its
 * contract, which is in the header of the stylesheet. It turns the refusals of the stylesheet into
 * exceptions. The stylesheet is compiled once. Each call gets its own transformer, so one instance
 * serves concurrent calls.
 *
 * <p>{@link #parse} parses the files of a package while it reads them. Each file gets a URI in a
 * scheme of its own, {@code formex:/{name}}. The stylesheet reads the files that a package refers
 * to with {@code doc(resolve-uri(@FILE, base-uri(.)))}. A resolver answers with the parsed file.
 * Nothing outside the package can be read in this way.
 *
 * <p>Formex files can be large. For example, one annex has 104 MB with more than 100,000 entity
 * references ({@code &amp;}). Since JDK 24, the XML parser of the JDK stops at 100,000 ({@code
 * jdk.xml.maxGeneralEntitySizeLimit}). Thus the constructor removes that limit on the processor
 * that it gets, for the documents that Saxon parses. Formex has no DTD, so there are no entities to
 * expand other than the predefined ones.
 */
final class FormexConverter {

  static final String STYLESHEET =
      "/dev/groknull/kludgeworks/formex/converter/xslt/formex-to-legal-text.xsl";
  static final String VERSION = "/dev/groknull/kludgeworks/formex/converter/version.txt";
  static final String ERRORS = "https://kludgeworks.groknull.dev/ns/formex-converter";

  /**
   * The location of the files of a package, as the stylesheet sees them. For example, {@code
   * formex:/L_2009330EN.01008001.xml}.
   */
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

  /**
   * Returns the version of the converter, from {@code version.txt}. The stylesheet stamps it on
   * every document.
   */
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
   * One XML file of a package, parsed while it was read. It holds the tree of the file, or the
   * reason why the file is not XML. It also holds the size of the file, in bytes.
   *
   * <p>A file that is not XML fails a conversion only if the stylesheet reads it. A package may
   * hold files that the stylesheet never reads.
   */
  record Document(
      String name, long size, @Nullable XdmNode tree, @Nullable SaxonApiException failure) {
    String uri() {
      return PACKAGE + name;
    }
  }

  /** Parses the file {@code name} of the package from {@code in} while it reads it. */
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
   * Converts the document of one package. The document is the first that applies of these:
   *
   * <ol>
   *   <li>the CONS.ACT or CONS.OTH file of a consolidation
   *   <li>the package descriptor (DOC)
   *   <li>the largest XML file
   * </ol>
   *
   * <p>The other files are the files that the document refers to.
   *
   * @param documents the parsed XML files of the package
   * @param celex the CELEX number of the document, as Cellar has it. For an OJ act, Formex does not
   *     carry the number. For a consolidation, its Formex only approximates the number. The
   *     document must be the one that the number names. It is null if the number is not known.
   * @return the legal-text document
   * @throws OutOfScopeException if the document is not a legal act. For example, notices, case law
   *     or the tables of contents of the OJ.
   * @throws MisfiledFormexException if the document is not the one that {@code celex} names
   * @throws UnsupportedFormexException if it uses Formex that the converter does not handle yet
   * @throws ConversionException if it cannot be converted for another reason. For example, no file
   *     of the package is XML, or a file that it refers to is missing or is not XML.
   */
  XdmNode convert(List<Document> documents, @Nullable String celex) {
    var document = main(documents);
    var transformer = stylesheet.load30();
    transformer.setResourceResolver(resolver(documents));
    var result = new XdmDestination();
    try {
      if (celex != null) {
        transformer.setStylesheetParameters(Map.of(CELEX, new XdmAtomicValue(celex)));
      }
      // The document is also the global context item, as on the Saxon command line. The global
      // variables of the stylesheet read it, for example the files of the package and its
      // amendment markers.
      transformer.setGlobalContextItem(document);
      transformer.applyTemplates(document, result);
    } catch (SaxonApiException e) {
      throw translate(e, Objects.toString(e.getMessage()));
    }
    return result.getXdmNode();
  }

  private static ConversionException translate(SaxonApiException e, String message) {
    var code = e.getErrorCode();
    if (code != null && ERRORS.equals(code.getNamespaceURI())) {
      return switch (code.getLocalName()) {
        case "out-of-scope" -> new OutOfScopeException(message, e);
        case "misfiled" -> new MisfiledFormexException(message, e);
        case "unknown" -> new UnsupportedFormexException(message, e);
        default -> new ConversionException(message, e);
      };
    }
    return new ConversionException(message, e);
  }

  /**
   * Returns the document to convert. It looks only at the files that are XML, in the order of their
   * names.
   */
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
   * Returns a resolver that gives the files of the package to {@code doc()}, by their {@code
   * formex:} URI. Any other URI belongs to the stylesheet, for example {@code subdivision.rdf}.
   * Saxon resolves those as it would without a resolver.
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
