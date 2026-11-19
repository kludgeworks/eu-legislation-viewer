// SPDX-License-Identifier: MIT
package dev.groknull.kludgeworks.formex.converter;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.io.StringReader;
import java.nio.file.Files;
import java.util.Optional;
import javax.xml.transform.stream.StreamSource;
import net.sf.saxon.s9api.Processor;
import net.sf.saxon.s9api.SaxonApiException;
import net.sf.saxon.s9api.XdmNode;
import org.junit.jupiter.api.Test;

/**
 * Cells of text alone, which tables.xsl converts on a path of their own: under an amendment marker
 * opened in an earlier cell, such a cell still carries its provenance. No fixture has such a
 * marker, so the test adds one to 2012/478's list of persons: opened in a row's first cell, closed
 * in its third.
 */
class TablesTest {
  static final Processor SAXON = new Processor(false);
  static final Stylesheet STYLESHEET = new Stylesheet(SAXON);
  static final String LEGAL_TEXT = "https://kludgeworks.groknull.dev/ns/legal-text/1";

  static final String ROW =
      "<ROW><CELL COL=\"1\">1.</CELL><CELL COL=\"2\">Drex Technologies Holding S.A.</CELL>"
          + "<CELL COL=\"3\">Registered in Luxembourg";
  static final String ROW_UNDER_A_MARKER =
      "<ROW><CELL COL=\"1\">1.<?CLG.MDFO ID=\"O000009M009000\" IDREF=\"C000009M009000\""
          + " ACTION=\"REPLACED\" LEVEL=\"STRUCTURE\" COMMAND=\"EXPLICIT\" ACTIVE.DOC=\"312D0478\""
          + " ACTIVE.LOC=\"TO\" MOD.LEVEL=\"1\"?></CELL>"
          + "<CELL COL=\"2\">Drex Technologies Holding S.A.</CELL>"
          + "<CELL COL=\"3\"><?CLG.MDFC ID=\"C000009M009000\" IDREF=\"O000009M009000\"?>Registered"
          + " in Luxembourg";

  @Test
  void aCellOfTextUnderAMarkerOpenedInAnEarlierCellCarriesItsProvenance()
      throws IOException, SaxonApiException, Stylesheet.Failure {
    var legalText = convert(ROW_UNDER_A_MARKER);

    assertThat(marker(legalText, "Drex Technologies Holding S.A.")).isEqualTo("O000009M009000");
    assertThat(marker(legalText, "1.")).isEmpty();
  }

  @Test
  void aCellOfTextUnderNoMarkerIsOneParagraphOfIt()
      throws IOException, SaxonApiException, Stylesheet.Failure {
    var legalText = convert(ROW);

    assertThat(
            evaluate(
                legalText,
                "count(//l:td[l:p/l:t/@v = 'Drex Technologies Holding S.A.']/node()),"
                    + " count(//l:td[l:p/l:t/@v = 'Drex Technologies Holding S.A.']/l:p/node())"))
        .isEqualTo("1 1");
    assertThat(marker(legalText, "Drex Technologies Holding S.A.")).isEmpty();
  }

  private static XdmNode convert(String row) throws IOException, Stylesheet.Failure {
    var input = Manifestations.named("formex/05.22/02012D0478-20120817").input();
    var formex = Files.readString(input);
    assertThat(formex).containsOnlyOnce(ROW);
    return STYLESHEET.transform(
        new StreamSource(new StringReader(formex.replace(ROW, row)), input.toUri().toString()),
        Optional.empty());
  }

  /** The marker of the provenance of the cell whose text is {@code text}; empty if it has none. */
  private static String marker(XdmNode legalText, String text) throws SaxonApiException {
    return evaluate(
        legalText, "string(//l:td[l:p/l:t/@v = '" + text + "']/l:p/l:provenance/@marker)");
  }

  private static String evaluate(XdmNode legalText, String expression) throws SaxonApiException {
    var xpath = SAXON.newXPathCompiler();
    xpath.declareNamespace("l", LEGAL_TEXT);
    return xpath
        .evaluate("string-join((" + expression + ") ! string(), ' ')", legalText)
        .toString();
  }
}
