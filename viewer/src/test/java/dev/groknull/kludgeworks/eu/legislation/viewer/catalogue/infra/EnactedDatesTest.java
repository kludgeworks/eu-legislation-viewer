// SPDX-License-Identifier: MIT
package dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.infra;

import static org.assertj.core.api.Assertions.assertThat;

import dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.ActDate.Kind;
import dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.ActDate.Provision;
import dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.ActIdentifier;
import dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.WhenAnActApplies.EnactedDate;
import dev.groknull.kludgeworks.eu.legislation.viewer.cellar.CellarException;
import dev.groknull.kludgeworks.eu.legislation.viewer.cellar.FormexManifestation;
import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.function.Function;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.ResourceAccessException;

/**
 * Tests how {@link EnactedDates} parses English Formex articles and classifies EU date formulas.
 */
class EnactedDatesTest {
  private static final ActIdentifier ACT = new ActIdentifier("32020R0910");
  private static final URI DOC_URI = URI.create("http://example.org/doc.xml");
  private static final URI MAIN_URI = URI.create("http://example.org/main.xml");

  @Test
  void findsMainXmlFileFromDocDescriptor() {
    var docXml =
        """
        <?xml version="1.0" encoding="utf-8"?>
        <DOC><FMX><DOC.MAIN.PUB><REF.PHYS FILE="L_2020208EN.01004301.xml" TYPE="DOC.XML"/></DOC.MAIN.PUB></FMX></DOC>
        """;
    assertThat(
            EnactedDates.parseMainXmlRef(
                ACT, new ByteArrayInputStream(docXml.getBytes(StandardCharsets.UTF_8))))
        .contains("L_2020208EN.01004301.xml");
  }

  @Test
  void classifiesDayOfPublication() {
    var xml =
        enactingArticle(
            "4",
            "This Regulation shall enter into force on the day of its publication in the"
                + " <HT TYPE=\"ITALIC\">Official Journal of the European Union</HT>.");

    assertThat(classify(xml, Kind.ENTRY_INTO_FORCE, List.of(article("4"))))
        .isEqualTo(new EnactedDate.OnPublication(0));
  }

  @Test
  void classifiesDayFollowingPublication() {
    var xml =
        enactingArticle(
            "5",
            "This Regulation shall enter into force the day following that of its publication in"
                + " the Official Journal of the European Union.");

    assertThat(classify(xml, Kind.ENTRY_INTO_FORCE, List.of(article("5"))))
        .isEqualTo(new EnactedDate.OnPublication(1));
  }

  @Test
  void classifiesNthDayFollowingPublication() {
    var xml =
        enactingArticle(
            "6",
            "This Regulation shall enter into force on the twentieth day following that of its"
                + " publication in the Official Journal of the European Union.");

    assertThat(classify(xml, Kind.ENTRY_INTO_FORCE, List.of(article("6"))))
        .isEqualTo(new EnactedDate.OnPublication(20));
  }

  @Test
  void classifiesFixedDate() {
    var xml =
        enactingArticle(
            "6",
            "<PARAG IDENTIFIER=\"006.002\"><NO.PARAG>2.</NO.PARAG><ALINEA>It shall apply from"
                + " <DATE ISO=\"20210401\">1 April 2021</DATE>.</ALINEA></PARAG>");

    assertThat(classify(xml, Kind.APPLICATION, List.of(article("6(2)"))))
        .isEqualTo(new EnactedDate.Fixed(LocalDate.of(2021, 4, 1)));
  }

  @Test
  void classifiesRetroactiveDate() {
    var xml =
        enactingArticle(
            "2",
            "This Regulation shall enter into force with retroactive effect as from"
                + " <DATE ISO=\"20170218\">18 February 2017</DATE>.");

    assertThat(classify(xml, Kind.ENTRY_INTO_FORCE, List.of(article("2"))))
        .isEqualTo(new EnactedDate.Fixed(LocalDate.of(2017, 2, 18)));
  }

  @Test
  void returnsUnknownForSentenceWithPublicationAndADate() {
    var xml =
        enactingArticle(
            "2",
            "This Regulation shall enter into force on <DATE ISO=\"20210401\">1 April 2021</DATE>"
                + " following publication.");

    assertThat(classify(xml, Kind.ENTRY_INTO_FORCE, List.of(article("2"))))
        .isEqualTo(new EnactedDate.Unknown());
  }

  @Test
  void returnsUnknownWhenDateBelongsToAnotherEventInTheSentence() {
    var xml =
        enactingArticle(
            "2",
            "This Regulation shall enter into force on the date of entry into force of the"
                + " Agreement signed on <DATE ISO=\"20190505\">5 May 2019</DATE>.");

    assertThat(classify(xml, Kind.ENTRY_INTO_FORCE, List.of(article("2"))))
        .isEqualTo(new EnactedDate.Unknown());
  }

  @Test
  void ignoresQuotedArticlesInsideAmendingActQuotations() {
    var xml =
        """
        <?xml version="1.0" encoding="utf-8"?>
        <ACT>
          <ENACTING.TERMS>
            <ARTICLE IDENTIFIER="001">
              <TI.ART>Article 1</TI.ART>
              <ALINEA>Article 5 of Regulation (EU) 2015/1998 is replaced by the following:
                <QUOT.S>
                  <ARTICLE IDENTIFIER="005">
                    <TI.ART>Article 5</TI.ART>
                    <ALINEA>It shall enter into force on <DATE ISO="20250101">1 January 2025</DATE>.</ALINEA>
                  </ARTICLE>
                </QUOT.S>
              </ALINEA>
            </ARTICLE>
            <ARTICLE IDENTIFIER="002">
              <TI.ART>Article 2</TI.ART>
              <ALINEA>This Regulation shall enter into force on the day following that of its publication in the Official Journal of the European Union.</ALINEA>
            </ARTICLE>
          </ENACTING.TERMS>
        </ACT>
        """;
    var articles =
        EnactedDates.parseArticles(
            ACT, new ByteArrayInputStream(xml.getBytes(StandardCharsets.UTF_8)));

    assertThat(
            EnactedDates.classifyArticles(
                articles, Kind.ENTRY_INTO_FORCE, List.of(article("1")), false))
        .isEqualTo(new EnactedDate.Unknown());
    assertThat(EnactedDates.classifyArticles(articles, Kind.ENTRY_INTO_FORCE, List.of(), false))
        .isEqualTo(new EnactedDate.OnPublication(1));
  }

  @Test
  void rejectsProvisionSubjectForWholeActAndRequiresProvisionSubjectForPart() {
    var partClause =
        enactingArticle(
            "6", "Article 3 shall apply from <DATE ISO=\"20210401\">1 April 2021</DATE>.");
    var wholeActClause =
        enactingArticle(
            "6", "This Regulation shall apply from <DATE ISO=\"20210601\">1 June 2021</DATE>.");
    var nonActSubject =
        enactingArticle(
            "6",
            "Member States shall apply those rules from <DATE ISO=\"20210601\">1 June"
                + " 2021</DATE>.");

    // A sentence naming a provision before the verb does not settle a whole-act date.
    assertThat(classify(partClause, Kind.APPLICATION, List.of(article("6")), false))
        .isEqualTo(new EnactedDate.Unknown());
    // A sentence whose subject is not the act (nor a provision) does not settle a whole-act date.
    assertThat(classify(nonActSubject, Kind.APPLICATION, List.of(article("6")), false))
        .isEqualTo(new EnactedDate.Unknown());
    // Conversely, a forPart date accepts only a sentence that names a provision before the verb.
    assertThat(classify(partClause, Kind.APPLICATION, List.of(article("6")), true))
        .isEqualTo(new EnactedDate.Fixed(LocalDate.of(2021, 4, 1)));
    assertThat(classify(wholeActClause, Kind.APPLICATION, List.of(article("6")), true))
        .isEqualTo(new EnactedDate.Unknown());
  }

  @Test
  void returnsEmptyArticlesWhenNoEnglishManifestationExists() {
    var articles =
        EnactedDates.lookup(
            ACT, Optional::empty, failingXml(new AssertionError("should not read stream")));

    assertThat(articles).isEmpty();
  }

  @Test
  void returnsEmptyArticlesWithoutFailingWhenManifestationOrContentStreamFails() {
    var manifest =
        new FormexManifestation(
            URI.create("http://example.org/manif"),
            List.of(
                new FormexManifestation.File("main.doc.xml", DOC_URI, "application/xml", null),
                new FormexManifestation.File("main.xml", MAIN_URI, "application/xml", null)));

    assertThat(
            EnactedDates.lookup(
                ACT,
                () -> {
                  throw new CellarException("SPARQL failed");
                },
                failingXml(new AssertionError("should not read stream"))))
        .isEmpty();

    assertThat(
            EnactedDates.lookup(
                ACT,
                () -> Optional.of(manifest),
                failingXml(new ResourceAccessException("Connection reset"))))
        .isEmpty();

    assertThat(
            EnactedDates.lookup(
                ACT,
                () -> Optional.of(manifest),
                failingXml(new UncheckedIOException(new java.io.IOException("corrupt gzip")))))
        .isEmpty();
  }

  private static EnactedDates.XmlReader failingXml(Throwable failure) {
    return new EnactedDates.XmlReader() {
      @Override
      public <T> T read(URI uri, Function<InputStream, T> reader) {
        if (failure instanceof RuntimeException runtime) {
          throw runtime;
        }
        throw (Error) failure;
      }
    };
  }

  private static EnactedDate classify(String xml, Kind kind, List<Provision> provisions) {
    return classify(xml, kind, provisions, false);
  }

  private static EnactedDate classify(
      String xml, Kind kind, List<Provision> provisions, boolean forPart) {
    var articles =
        EnactedDates.parseArticles(
            ACT, new ByteArrayInputStream(xml.getBytes(StandardCharsets.UTF_8)));
    return EnactedDates.classifyArticles(articles, kind, provisions, forPart);
  }

  private static String enactingArticle(String number, String body) {
    var content = body.startsWith("<PARAG") ? body : "<ALINEA>" + body + "</ALINEA>";
    return """
    <?xml version="1.0" encoding="utf-8"?>
    <ACT>
      <ENACTING.TERMS>
        <ARTICLE IDENTIFIER="00%s">
          <TI.ART>Article %s</TI.ART>
          %s
        </ARTICLE>
      </ENACTING.TERMS>
    </ACT>
    """
        .formatted(number, number, content);
  }

  private static Provision article(String number) {
    return new Provision(Provision.Type.ARTICLE, number);
  }
}
