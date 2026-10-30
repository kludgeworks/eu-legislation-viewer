// SPDX-License-Identifier: MIT
package dev.groknull.kludgeworks.eu.legislation.viewer.text.infra;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Predicate;
import java.util.regex.Pattern;
import javax.xml.transform.Source;
import javax.xml.transform.stream.StreamSource;
import net.sf.saxon.lib.FeatureKeys;
import net.sf.saxon.s9api.Processor;
import net.sf.saxon.s9api.QName;
import net.sf.saxon.s9api.SaxonApiException;
import net.sf.saxon.s9api.XdmAtomicValue;
import net.sf.saxon.s9api.XdmDestination;
import net.sf.saxon.s9api.XdmNode;
import net.sf.saxon.s9api.XsltExecutable;
import org.jspecify.annotations.Nullable;

/**
 * Turns Formex 4 into legal-text XML with {@code formex-to-legal-text.xsl}, run by its contract
 * (the stylesheet's header), its refusals turned into exceptions: the text module's adapter on
 * //formex-converter's stylesheets. The stylesheet is compiled once; each call gets its own
 * transformer, so one instance serves concurrent calls.
 *
 * <p>Formex files can be large: an annex of 104 MB with over 100,000 entity references (&amp;amp;).
 * Since JDK 24 the JDK's XML parser stops at 100,000 ({@code jdk.xml.maxGeneralEntitySizeLimit}),
 * so the constructor lifts that limit on the processor it is given, for the documents Saxon parses:
 * the input when it is a stream, and the files the stylesheet reads. Formex has no DTD, so there
 * are no entities to expand beyond the predefined ones.
 */
final class FormexConverter {

  static final String STYLESHEET =
      "/dev/groknull/kludgeworks/formex/converter/xslt/formex-to-legal-text.xsl";
  static final String ERRORS = "https://kludgeworks.groknull.dev/ns/formex-converter";
  private static final Pattern ROOT_ELEMENT = Pattern.compile("<([A-Z][A-Z0-9.]*)[\\s>/]");
  private static final QName CELEX = new QName("celex");

  private final Processor processor;
  private final XsltExecutable stylesheet;

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
  }

  /**
   * Converts one document.
   *
   * @param formex a consolidation (CONS.ACT, CONS.OTH), an OJ act (ACT, AGR, CORR) or its package
   *     descriptor (DOC). Its system id must be set: the files it refers to (annexes, included
   *     documents) are resolved against it.
   * @param celex the CELEX number of an OJ act, which Formex doesn't carry; empty for a
   *     consolidation, which carries its own
   * @return the legal-text document
   * @throws OutOfScopeException if the document isn't a legal act (notices, case law, the OJ's
   *     tables of contents)
   * @throws UnsupportedFormexException if it uses Formex the converter doesn't handle yet
   * @throws ConversionException if it can't be converted for another reason
   */
  XdmNode convert(Source formex, Optional<String> celex) {
    var transformer = stylesheet.load30();
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
      var document = processor.newDocumentBuilder().build(formex);
      transformer.setGlobalContextItem(document);
      transformer.applyTemplates(document, result);
    } catch (SaxonApiException e) {
      var refusal = reason.get();
      throw translate(e, refusal != null ? refusal : String.valueOf(e.getMessage()));
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

  /**
   * The file of a manifestation to convert, from the folder holding its files (each named as the
   * package refers to it): a consolidation's CONS.ACT or CONS.OTH file, else the package descriptor
   * (DOC), else the largest XML file.
   *
   * @throws IllegalArgumentException if the folder holds no XML file
   */
  static Path input(Path folder) {
    var files = xmlFiles(folder);
    if (files.isEmpty()) {
      throw new IllegalArgumentException("No Formex file in " + folder);
    }
    return first(files, root -> root.startsWith("CONS."))
        .or(() -> first(files, "DOC"::equals))
        .orElseGet(
            () ->
                files.stream().max(Comparator.comparingLong(FormexConverter::size)).orElseThrow());
  }

  private static List<Path> xmlFiles(Path folder) {
    try (var files = Files.list(folder)) {
      return files.filter(p -> p.getFileName().toString().endsWith(".xml")).sorted().toList();
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }

  private static Optional<Path> first(List<Path> files, Predicate<String> root) {
    return files.stream().filter(f -> root.test(rootElement(f))).findFirst();
  }

  /** The root element's name, read from the start of the file. */
  private static String rootElement(Path file) {
    try (InputStream in = Files.newInputStream(file)) {
      var matcher = ROOT_ELEMENT.matcher(new String(in.readNBytes(4096), StandardCharsets.UTF_8));
      return matcher.find() ? matcher.group(1) : "";
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }

  private static long size(Path file) {
    try {
      return Files.size(file);
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }
}
