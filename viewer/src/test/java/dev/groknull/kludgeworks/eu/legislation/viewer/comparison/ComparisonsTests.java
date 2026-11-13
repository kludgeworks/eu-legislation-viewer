// SPDX-License-Identifier: MIT
package dev.groknull.kludgeworks.eu.legislation.viewer.comparison;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import dev.groknull.kludgeworks.eu.legislation.viewer.TestcontainersConfiguration;
import dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.NeighboursBuilt;
import dev.groknull.kludgeworks.eu.legislation.viewer.content.Content;
import dev.groknull.kludgeworks.eu.legislation.viewer.content.Element;
import dev.groknull.kludgeworks.eu.legislation.viewer.content.TextId;
import dev.groknull.kludgeworks.eu.legislation.viewer.content.model.LegalTexts;
import dev.groknull.kludgeworks.eu.legislation.viewer.language.Language;
import java.io.StringReader;
import java.time.Duration;
import java.util.Map;
import javax.xml.transform.stream.StreamSource;
import net.sf.saxon.s9api.QName;
import net.sf.saxon.s9api.SaxonApiException;
import net.sf.saxon.s9api.XdmAtomicValue;
import net.sf.saxon.s9api.XdmDestination;
import net.sf.saxon.s9api.XdmNode;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Tests comparisons through the module's API. The texts are stored by the content module, and the
 * comparisons are kept in Postgres in a container. The texts are the consolidations of 2015/1998 of
 * 22 March and 1 October 2026.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@Import(TestcontainersConfiguration.class)
class ComparisonsTests {
  @Autowired Comparisons comparisons;
  @Autowired Content content;
  @Autowired ApplicationEventPublisher events;
  @Autowired TransactionTemplate transaction;
  @Autowired JdbcClient jdbc;

  @Test
  void worksOutAComparisonTheFirstTimeAndKeepsIt() {
    var march = store("02015R1998-20260322");
    var october = store("02015R1998-20261001");

    var worked = comparisons.between(march, october).orElseThrow();

    assertThat(worked.differences()).hasSize(6);
    assertThat(kept(march, october)).isOne();
    assertThat(comparisons.between(march, october)).contains(worked);
    assertThat(kept(march, october)).isOne();
  }

  @Test
  void worksOutTheComparisonOfNeighboursBuiltAheadOfAReader() {
    var january = store("02015R1998-20260101");
    var march = store("02015R1998-20260322");

    transaction.executeWithoutResult(
        status -> events.publishEvent(new NeighboursBuilt(january, march)));

    await().atMost(Duration.ofMinutes(1)).until(() -> kept(january, march) == 1);
  }

  @Test
  void worksOutAgainAComparisonThatOtherRulesWorkedOut() {
    var march = store("02015R1998-20260322");
    var october = store("02015R1998-20261001");
    var worked = comparisons.between(march, october).orElseThrow();
    jdbc.sql("update text_comparison set differ_version = 'older' where to_text = ?")
        .param(october.value())
        .update();

    assertThat(comparisons.between(march, october)).contains(worked);
    assertThat(
            jdbc.sql("select differ_version from text_comparison where to_text = ?")
                .param(october.value())
                .query(String.class)
                .single())
        .isNotEqualTo("older");
  }

  @Test
  void removesTheComparisonsOfATextThatIsReplaced() {
    var march = store("02015R1998-20260322");
    var october = store("02015R1998-20261001");
    assertThat(comparisons.between(march, october)).isPresent();

    // The text of October is stored again, as another text
    var replaced =
        content.store(
            "02015R1998-20261001",
            Language.ENG,
            "test",
            LegalTexts.sample("06.02.1/02015R1998-20260101.xml"));

    assertThat(replaced).isNotEqualTo(october);
    await().atMost(Duration.ofMinutes(1)).until(() -> kept(march, october) == 0);
  }

  @Test
  void removesAComparisonKeptAfterOneOfItsTextsIsGone() {
    var march = store("02015R1998-20260322");
    var october = store("02015R1998-20261001");
    assertThat(comparisons.between(march, october)).isPresent();
    // As if October were replaced while the comparison was worked out, before it was kept
    jdbc.sql("delete from text where id = ?").param(october.value()).update();

    transaction.executeWithoutResult(
        status -> events.publishEvent(new ComparisonKept(march, october)));

    await().atMost(Duration.ofMinutes(1)).until(() -> kept(march, october) == 0);
  }

  @Test
  void hasNoComparisonOfATextNotStored() {
    var march = store("02015R1998-20260322");
    var none = new TextId(-1);

    assertThat(comparisons.between(march, none)).isEmpty();
    assertThat(kept(march, none)).isZero();
  }

  @Test
  void keepsAnAnnexInsertedWholeApartAndGivesItWholeByItself() throws SaxonApiException {
    var later = LegalTexts.sample("06.02.1/02018R2019-20250623.xml");
    var to = content.store("02018R2019-20250623", Language.ENG, "test", later);
    var from = content.store("02018R2019-without-anx", Language.ENG, "test", without(later, "anx"));

    var annex =
        comparisons.between(from, to).orElseThrow().differences().stream()
            .filter(each -> each.eId().equals("anx"))
            .findFirst()
            .orElseThrow();
    var whole = comparisons.difference(from, to, "anx").orElseThrow();

    // The comparison has its opening and size, and the annex is a row of its own
    assertThat(annex.annexInserted()).isTrue();
    assertThat(annex.shown().children()).extracting(Element::name).doesNotContain("unit");
    assertThat(annex.whole()).isEqualTo(Difference.Whole.of(whole.shown()));
    assertThat(annex.size().words()).isPositive();
    assertThat(whole.whole()).isNull();
    assertThat(whole.shown().children()).extracting(Element::name).contains("unit");
    assertThat(
            jdbc.sql(
                    "select count(*) from text_comparison_whole where from_text = ? and to_text ="
                        + " ?")
                .param(from.value())
                .param(to.value())
                .query(Long.class)
                .single())
        .isOne();
    assertThat(comparisons.difference(from, to, "art_1")).isEmpty();
  }

  /** Returns {@code text} without its unit {@code eId}. */
  private static XdmNode without(XdmNode text, String eId) throws SaxonApiException {
    var xslt =
        """
        <xsl:stylesheet version="3.0" xmlns:xsl="http://www.w3.org/1999/XSL/Transform"
            xmlns:lt="https://kludgeworks.groknull.dev/ns/legal-text/1">
          <xsl:param name="eId"/>
          <xsl:mode on-no-match="shallow-copy"/>
          <xsl:template match="lt:unit[@eId = $eId]"/>
        </xsl:stylesheet>
        """;
    var transformer =
        LegalTexts.SAXON
            .newXsltCompiler()
            .compile(new StreamSource(new StringReader(xslt)))
            .load30();
    transformer.setStylesheetParameters(Map.of(new QName("eId"), new XdmAtomicValue(eId)));
    var result = new XdmDestination();
    transformer.applyTemplates(text, result);
    return result.getXdmNode();
  }

  /** Stores the sample {@code celex} of 2015/1998 in English, under its CELEX. */
  private TextId store(String celex) {
    return content.store(
        celex, Language.ENG, "test", LegalTexts.sample("06.02.1/" + celex + ".xml"));
  }

  private long kept(TextId from, TextId to) {
    return jdbc.sql("select count(*) from text_comparison where from_text = ? and to_text = ?")
        .param(from.value())
        .param(to.value())
        .query(Long.class)
        .single();
  }
}
