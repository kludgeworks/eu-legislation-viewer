// SPDX-License-Identifier: MIT
package dev.groknull.kludgeworks.eu.legislation.viewer.text.infra;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
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
 * Whether normalisation kept all the text of its source. /compare/formex-values.xsl and
 * /compare/legal-text-values.xsl reduce the Formex and the legal text to the same list of values,
 * written independently of the normaliser. Every source value must occur in the output as often
 * as in the source (a value of kind "once" at least once); extra output values are fine, the
 * normaliser makes some up (quotation marks whose partner sits in another element).
 */
final class TextConservation {

  /** A source value that the output has fewer times than the source. */
  record Missing(String text, long times, String where) {
    @Override
    public String toString() {
      return times + " × \"" + (text.length() > 80 ? text.substring(0, 77) + "…" : text) + "\" at " + where;
    }
  }

  private record Value(String text, String kind, String file, String path) {}

  private final XsltExecutable formexValues;
  private final XsltExecutable legalTextValues;

  TextConservation(Processor processor) {
    formexValues = compile(processor, "/compare/formex-values.xsl");
    legalTextValues = compile(processor, "/compare/legal-text-values.xsl");
  }

  /** The source values the output lacks, in source order. */
  List<Missing> missing(Source formex, XdmNode legalText) {
    var have =
        values(legalTextValues, legalText.asSource()).stream()
            .collect(Collectors.groupingBy(Value::text, Collectors.counting()));
    Map<String, List<Value>> wanted =
        values(formexValues, formex).stream()
            .collect(Collectors.groupingBy(Value::text, LinkedHashMap::new, Collectors.toList()));
    var missing = new ArrayList<Missing>();
    wanted.forEach(
        (text, occurrences) -> {
          var once = occurrences.stream().anyMatch(v -> "once".equals(v.kind()));
          var shortfall = (once ? 1 : occurrences.size()) - have.getOrDefault(text, 0L);
          if (shortfall > 0) {
            var first = occurrences.getFirst();
            missing.add(new Missing(text, shortfall, first.file() + " " + first.path().replace("Q{}", "")));
          }
        });
    return missing;
  }

  private static List<Value> values(XsltExecutable stylesheet, Source source) {
    var result = new XdmDestination();
    try {
      stylesheet.load30().applyTemplates(source, result);
    } catch (SaxonApiException e) {
      throw new IllegalStateException(e);
    }
    Function<XdmNode, Value> value =
        v -> new Value(v.getStringValue(), v.attribute("kind"), v.attribute("file"), Objects.toString(v.attribute("path"), ""));
    return result.getXdmNode().select(Steps.path("values", "v")).map(value).toList();
  }

  private static XsltExecutable compile(Processor processor, String resource) {
    var url = Objects.requireNonNull(TextConservation.class.getResource(resource), resource);
    try {
      return processor.newXsltCompiler().compile(new StreamSource(url.toExternalForm()));
    } catch (SaxonApiException e) {
      throw new IllegalStateException(resource + " doesn't compile", e);
    }
  }
}
