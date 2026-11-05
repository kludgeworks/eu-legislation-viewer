// SPDX-License-Identifier: MIT
package dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.web.view;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

import dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.Act;
import dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.ActIdentifier;
import dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.Preparation;
import dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.Text;
import dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.Version;
import dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.infra.RecordedActs;
import dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.web.view.FailureView.Action;
import dev.groknull.kludgeworks.eu.legislation.viewer.ingest.Failure;
import dev.groknull.kludgeworks.eu.legislation.viewer.language.Language;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * Why 2015/1998's version of 31 December 2021 couldn't be made, as its page says it (Cellar's
 * versions, recorded): its words and only the actions that can help, for each kind of failure.
 */
class FailureViewTest {
  static final Act AVSEC = RecordedActs.act(new ActIdentifier("32015R1998"));
  static final String PAGES = "/eli/reg_impl/2015/1998/";
  static final String SELF = PAGES + "2021-12-31/eng";
  static final Version VERSION = version("02015R1998-20211231");

  @Test
  void offersToTryAgainWhenThePublicationsOfficeDidntAnswer() {
    var failure = FailureView.reading(AVSEC, VERSION, Failure.UNAVAILABLE, Language.ENG, SELF);

    assertThat(failure.heading()).isEqualTo("The Publications Office didn't answer");
    assertThat(failure.fault()).isTrue();
    assertThat(failure.retry()).isEqualTo(SELF);
    assertThat(failure.actions())
        .extracting(Action::label, Action::href, Action::button)
        .containsExactly(
            tuple("Read the version before (from 1 July 2021)", PAGES + "2021-07-01/eng", true),
            tuple("Read the version after (from 1 March 2022)", PAGES + "2022-03-01/eng", true),
            tuple(
                "Read this version on EUR-Lex",
                "https://eur-lex.europa.eu/legal-content/EN/TXT/?uri=CELEX:02015R1998-20211231",
                false));
    assertThat(failure.reference()).isNull();
  }

  @Test
  void offersNoTryAgainWhereItCantHelpButAReferenceWhereAReportCan() {
    var unsupported = FailureView.reading(AVSEC, VERSION, Failure.UNSUPPORTED, Language.ENG, SELF);
    var error = FailureView.reading(AVSEC, VERSION, Failure.ERROR, Language.ENG, SELF);

    assertThat(unsupported.retry()).isNull();
    assertThat(unsupported.reference()).isEqualTo("02015R1998-20211231 ENG");
    assertThat(error.retry()).isEqualTo(SELF);
    assertThat(error.reference()).isEqualTo("02015R1998-20211231 ENG");
  }

  @Test
  void pointsToTheLanguagesAVersionIsInWhenItHasNoFormexInThisOne() {
    var failure =
        FailureView.reading(
            AVSEC, VERSION, Failure.NO_FORMEX, Language.GLE, PAGES + "2021-12-31/gle");

    assertThat(failure.heading()).isEqualTo("Not available in Irish");
    assertThat(failure.fault()).isFalse();
    assertThat(failure.retry()).isNull();
    assertThat(failure.facts()).extracting(FailureView.Fact::term).contains("Exists in");
    assertThat(failure.actions().getFirst())
        .isEqualTo(new Action("Read it in English", PAGES + "2021-12-31/eng", true));
  }

  @Test
  void offersTheComparisonOfTheVersionsEitherSideFirst() {
    var from = version("02015R1998-20210701");

    var failure =
        FailureView.comparing(
            AVSEC,
            from,
            VERSION,
            VERSION,
            Failure.UNAVAILABLE,
            Language.ENG,
            PAGES + "eng/compare?from=2021-07-01&to=2021-12-31");

    assertThat(failure.actions().getFirst())
        .isEqualTo(
            new Action(
                "Compare the versions on either side instead",
                PAGES + "eng/compare?from=2021-07-01&to=2022-03-01",
                true));
  }

  @Test
  void saysTheActCouldntBePreparedWithItsFirstFailure() {
    var texts = new LinkedHashMap<Version, Text>();
    texts.put(version("02015R1998-20210701"), new Text.Failed(Failure.NO_FORMEX));
    texts.put(VERSION, new Text.Failed(Failure.UNAVAILABLE));
    var preparation =
        new Preparation(
            AVSEC.id(), Language.ENG, Instant.EPOCH, Instant.EPOCH, texts, List.of(), 0);

    var failure = FailureView.act(AVSEC, preparation, SELF);

    assertThat(failure.heading()).isEqualTo("This act couldn't be prepared");
    assertThat(failure.words())
        .isEqualTo(
            "None of its 2 versions could be converted. The first failure: the Publications"
                + " Office has no Formex of it in this language.");
    // one of them may answer now
    assertThat(failure.retry()).isEqualTo(SELF);
  }

  private static Version version(String celex) {
    return AVSEC.timeline().versions().stream()
        .filter(v -> v.celex().equals(celex))
        .findFirst()
        .orElseThrow();
  }
}
