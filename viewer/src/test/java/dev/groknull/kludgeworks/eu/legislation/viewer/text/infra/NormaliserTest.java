// SPDX-License-Identifier: MIT
package dev.groknull.kludgeworks.eu.legislation.viewer.text.infra;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

import java.io.StringReader;
import java.util.Optional;
import javax.xml.transform.stream.StreamSource;
import net.sf.saxon.s9api.Processor;
import org.junit.jupiter.api.Test;

/** How {@link Normaliser} reports what the stylesheet can't or won't normalise. */
class NormaliserTest {

  static final Normaliser NORMALISER = new Normaliser(new Processor(false));

  @Test
  void normalisesAnOjActWithItsCelex() {
    var legalText = NORMALISER.normalise(formex(act("<ENACTING.TERMS/>")), Optional.of("32026R0449"));
    assertThat(legalText.toString()).contains("act=\"32026R0449\"");
  }

  @Test
  void readsADocumentWithMoreEntityReferencesThanTheJdkAllows() {
    // a 104 MB annex has over 100,000 &amp;; the JDK's parser stops at 100,000 since JDK 24
    var text = "a&amp;b ".repeat(100_500);
    var legalText = NORMALISER.normalise(formex(act("<ENACTING.TERMS><ARTICLE><TI.ART>Article 1</TI.ART><ALINEA>" + text + "</ALINEA></ARTICLE></ENACTING.TERMS>")), Optional.of("32026R0449"));
    assertThat(legalText.toString()).contains("a&amp;b");
  }

  @Test
  void refusesADocumentThatIsNotALegalAct() {
    assertThatExceptionOfType(OutOfScopeException.class)
        .isThrownBy(() -> NORMALISER.normalise(formex("<JUDGMENT/>"), Optional.empty()))
        .withMessageContaining("JUDGMENT");
  }

  @Test
  void reportsFormexItDoesNotHandle() {
    assertThatExceptionOfType(UnsupportedFormexException.class)
        .isThrownBy(() -> NORMALISER.normalise(formex(act("<ENACTING.TERMS><NOT.FORMEX/></ENACTING.TERMS>")), Optional.of("32026R0449")))
        .withMessageContaining("NOT.FORMEX");
  }

  @Test
  void needsTheCelexOfAnOjAct() {
    assertThatExceptionOfType(NormalisationException.class)
        .isThrownBy(() -> NORMALISER.normalise(formex(act("<ENACTING.TERMS/>")), Optional.empty()))
        .isExactlyInstanceOf(NormalisationException.class)
        .withMessageContaining("celex");
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
