// SPDX-License-Identifier: MIT
package dev.groknull.kludgeworks.formex.converter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

import java.io.IOException;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.util.Optional;
import javax.xml.transform.stream.StreamSource;
import net.sf.saxon.s9api.Processor;
import net.sf.saxon.s9api.QName;
import org.junit.jupiter.api.Test;

/** The stylesheet's contract: its parameters, its version stamp and its error codes. */
class StylesheetTest {

  static final Stylesheet STYLESHEET = new Stylesheet(new Processor(false));

  @Test
  void convertsAnOjActWithItsCelex() throws Stylesheet.Failure {
    var legalText =
        STYLESHEET.transform(formex(act("<ENACTING.TERMS/>")), Optional.of("32026R0449"));
    assertThat(legalText.toString()).contains("act=\"32026R0449\"");
  }

  @Test
  void stampsTheConvertersVersion() throws IOException, Stylesheet.Failure {
    String version;
    try (var in = Stylesheet.class.getResourceAsStream("version.txt")) {
      version = new String(in.readAllBytes(), StandardCharsets.UTF_8).strip();
    }
    var legalText =
        STYLESHEET.transform(formex(act("<ENACTING.TERMS/>")), Optional.of("32026R0449"));
    assertThat(legalText.toString()).contains("converter=\"" + version + "\"");
  }

  @Test
  void refusesADocumentThatIsNotALegalAct() {
    assertThatExceptionOfType(Stylesheet.Failure.class)
        .isThrownBy(() -> STYLESHEET.transform(formex("<JUDGMENT/>"), Optional.empty()))
        .withMessageContaining("JUDGMENT")
        .satisfies(e -> assertThat(e.code()).isEqualTo(Stylesheet.OUT_OF_SCOPE));
  }

  @Test
  void reportsFormexItDoesNotHandle() {
    assertThatExceptionOfType(Stylesheet.Failure.class)
        .isThrownBy(
            () ->
                STYLESHEET.transform(
                    formex(act("<ENACTING.TERMS><NOT.FORMEX/></ENACTING.TERMS>")),
                    Optional.of("32026R0449")))
        .withMessageContaining("NOT.FORMEX")
        .satisfies(e -> assertThat(e.code()).isEqualTo(Stylesheet.UNKNOWN));
  }

  @Test
  void needsTheCelexOfAnOjAct() {
    assertThatExceptionOfType(Stylesheet.Failure.class)
        .isThrownBy(() -> STYLESHEET.transform(formex(act("<ENACTING.TERMS/>")), Optional.empty()))
        .withMessageContaining("celex")
        .satisfies(e -> assertThat(e.code()).isEqualTo(new QName(Stylesheet.ERRORS, "celex")));
  }

  private static String act(String content) {
    return "<ACT><BIB.INSTANCE><DOCUMENT.REF><COLL>L</COLL></DOCUMENT.REF><LG.DOC>EN</LG.DOC></BIB.INSTANCE>"
        + content
        + "</ACT>";
  }

  private static StreamSource formex(String xml) {
    return new StreamSource(new StringReader(xml), "urn:test:formex.xml");
  }
}
