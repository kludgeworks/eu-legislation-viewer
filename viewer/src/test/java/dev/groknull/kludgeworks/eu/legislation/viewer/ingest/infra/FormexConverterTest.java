// SPDX-License-Identifier: MIT
package dev.groknull.kludgeworks.eu.legislation.viewer.ingest.infra;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

import dev.groknull.kludgeworks.eu.legislation.viewer.ingest.model.ConversionException;
import dev.groknull.kludgeworks.eu.legislation.viewer.ingest.model.OutOfScopeException;
import dev.groknull.kludgeworks.eu.legislation.viewer.ingest.model.UnsupportedFormexException;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import net.sf.saxon.s9api.Processor;
import org.junit.jupiter.api.Test;

/**
 * Tests {@link FormexConverter}, the adapter on the stylesheet. The tests check that:
 *
 * <ul>
 *   <li>the adapter passes the CELEX number to the stylesheet,
 *   <li>it reads large documents,
 *   <li>it gives the files of a package to the stylesheet as the stylesheet reads them,
 *   <li>it reports as exceptions what the stylesheet cannot or will not convert.
 * </ul>
 *
 * <p>{@code StylesheetTest} in {@code //formex-converter} tests the contract of the stylesheet
 * itself.
 */
class FormexConverterTest {

  static final FormexConverter CONVERTER = new FormexConverter(new Processor(false));

  @Test
  void convertsAnOjActWithItsCelex() {
    var legalText = CONVERTER.convert(formex(act("<ENACTING.TERMS/>")), "32026R0449");
    assertThat(legalText.toString()).contains("act=\"32026R0449\"");
  }

  @Test
  void readsADocumentWithMoreEntityReferencesThanTheJdkAllows() {
    // A 104 MB annex has more than 100,000 &amp; references. Since JDK 24, the JDK's parser stops
    // at 100,000.
    var text = "a&amp;b ".repeat(100_500);
    var legalText =
        CONVERTER.convert(
            formex(
                act(
                    "<ENACTING.TERMS><ARTICLE><TI.ART>Article 1</TI.ART><ALINEA>"
                        + text
                        + "</ALINEA></ARTICLE></ENACTING.TERMS>")),
            "32026R0449");
    assertThat(legalText.toString()).contains("a&amp;b");
  }

  @Test
  void refusesADocumentThatIsNotALegalAct() {
    assertThatExceptionOfType(OutOfScopeException.class)
        .isThrownBy(() -> CONVERTER.convert(formex("<JUDGMENT/>"), null))
        .withMessageContaining("JUDGMENT");
  }

  @Test
  void reportsFormexItDoesNotHandle() {
    assertThatExceptionOfType(UnsupportedFormexException.class)
        .isThrownBy(
            () ->
                CONVERTER.convert(
                    formex(act("<ENACTING.TERMS><NOT.FORMEX/></ENACTING.TERMS>")), "32026R0449"))
        .withMessageContaining("NOT.FORMEX");
  }

  @Test
  void needsTheCelexOfAnOjAct() {
    assertThatExceptionOfType(ConversionException.class)
        .isThrownBy(() -> CONVERTER.convert(formex(act("<ENACTING.TERMS/>")), null))
        .isExactlyInstanceOf(ConversionException.class)
        .withMessageContaining("celex");
  }

  @Test
  void convertsAPackageWithAFileThatIsNotXmlButIsNeverRead() {
    var legalText =
        CONVERTER.convert(
            List.of(
                file("CONSLEG_1993D0052_0140010_EN_FMX.request.xml", "not XML"),
                file("act.xml", act("<ENACTING.TERMS/>"))),
            "32026R0449");
    assertThat(legalText.toString()).contains("act=\"32026R0449\"");
  }

  @Test
  void failsWhereTheDocumentRefersToAFileThePackageHasNot() {
    assertThatExceptionOfType(ConversionException.class)
        .isThrownBy(
            () ->
                CONVERTER.convert(
                    List.of(file("package.doc.xml", descriptor("act.xml"))), "32026R0449"))
        .withMessageContaining("The package has no file act.xml");
  }

  @Test
  void failsWhereAFileTheDocumentRefersToIsNotXml() {
    assertThatExceptionOfType(ConversionException.class)
        .isThrownBy(
            () ->
                CONVERTER.convert(
                    List.of(
                        file("package.doc.xml", descriptor("act.xml")), file("act.xml", "<ACT>")),
                    "32026R0449"))
        .withMessageContaining("act.xml isn't XML");
  }

  @Test
  void failsWhereNoFileOfThePackageIsXml() {
    assertThatExceptionOfType(ConversionException.class)
        .isThrownBy(() -> CONVERTER.convert(List.of(file("act.xml", "<ACT>")), null))
        .withMessageContaining("No file of the package is XML");
  }

  private static String descriptor(String act) {
    return "<DOC><FMX><DOC.MAIN.PUB><REF.PHYS FILE=\"" + act + "\"/></DOC.MAIN.PUB></FMX></DOC>";
  }

  private static String act(String content) {
    return "<ACT><BIB.INSTANCE><DOCUMENT.REF><COLL>L</COLL></DOCUMENT.REF><LG.DOC>EN</LG.DOC></BIB.INSTANCE>"
        + content
        + "</ACT>";
  }

  private static List<FormexConverter.Document> formex(String xml) {
    return List.of(file("formex.xml", xml));
  }

  private static FormexConverter.Document file(String name, String xml) {
    return CONVERTER.parse(name, new ByteArrayInputStream(xml.getBytes(StandardCharsets.UTF_8)));
  }
}
