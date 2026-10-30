// SPDX-License-Identifier: MIT
package dev.groknull.kludgeworks.formex.converter;

import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import javax.xml.transform.Source;
import javax.xml.transform.stream.StreamSource;
import net.sf.saxon.s9api.Processor;
import net.sf.saxon.s9api.SaxonApiException;
import net.sf.saxon.s9api.XdmDestination;
import net.sf.saxon.s9api.XdmNode;
import net.sf.saxon.s9api.XsltExecutable;
import net.sf.saxon.s9api.streams.Steps;

/**
 * Whether conversion kept the metadata the viewer relies on: identity, the Official Journal
 * reference, a consolidation's dates and what it integrates. /compare/formex-metadata.xsl and
 * /compare/legal-text-metadata.xsl reduce the Formex and the legal text to the same fields, written
 * independently of the converter, as {@link TextConservation} does for the text. Every source field
 * must occur in the output with its value; the rest of Formex's metadata isn't checked.
 */
final class MetadataConservation {

  /** A field of the source, with its value. */
  record Field(String name, String value) {
    @Override
    public String toString() {
      return name + " " + value;
    }
  }

  private final XsltExecutable formexMetadata;
  private final XsltExecutable legalTextMetadata;

  MetadataConservation(Processor processor) {
    formexMetadata = compile(processor, "/compare/formex-metadata.xsl");
    legalTextMetadata = compile(processor, "/compare/legal-text-metadata.xsl");
  }

  /** The source fields the output lacks, in source order. */
  List<Field> missing(Source formex, XdmNode legalText) {
    Set<Field> have = Set.copyOf(fields(legalTextMetadata, legalText.asSource()));
    return fields(formexMetadata, formex).stream()
        .distinct()
        .filter(field -> !have.contains(field))
        .collect(Collectors.toList());
  }

  private static List<Field> fields(XsltExecutable stylesheet, Source source) {
    var result = new XdmDestination();
    try {
      stylesheet.load30().applyTemplates(source, result);
    } catch (SaxonApiException e) {
      throw new IllegalStateException(e);
    }
    return result
        .getXdmNode()
        .select(Steps.path("fields", "field"))
        .map(f -> new Field(f.attribute("name"), f.getStringValue()))
        .toList();
  }

  private static XsltExecutable compile(Processor processor, String resource) {
    var url = Objects.requireNonNull(MetadataConservation.class.getResource(resource), resource);
    try {
      return processor.newXsltCompiler().compile(new StreamSource(url.toExternalForm()));
    } catch (SaxonApiException e) {
      throw new IllegalStateException(resource + " doesn't compile", e);
    }
  }
}
