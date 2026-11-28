// SPDX-License-Identifier: MIT
package dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.web.view;

import static org.assertj.core.api.Assertions.assertThat;

import dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.Act;
import dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.ActDate;
import dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.ActDates;
import dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.ActIdentifier;
import dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.Preparation;
import dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.Text;
import dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.Timeline;
import dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.Version;
import dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.infra.RecordedActs;
import dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.web.view.PreparingView.Fact;
import dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.web.view.PreparingView.Progress;
import dev.groknull.kludgeworks.eu.legislation.viewer.content.TextId;
import dev.groknull.kludgeworks.eu.legislation.viewer.language.Language;
import java.time.Instant;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.SequencedMap;
import org.junit.jupiter.api.Test;

/**
 * Tests what the preparing page says while the act waits behind the versions of other acts, and of
 * the versions of 2015/1998 that are worked on. The versions are Cellar's, recorded.
 */
class PreparingViewTest {
  static final Act AVSEC = RecordedActs.act(new ActIdentifier("32015R1998"));

  @Test
  void listsTheVersionsBeingConvertedAndThoseToBeTriedAgain() {
    var versions = AVSEC.timeline().in(Language.ENG).orElseThrow().versions();
    var texts = new LinkedHashMap<Version, Text>();
    versions.forEach(version -> texts.put(version, new Text.Ready(new TextId(1))));
    texts.put(versions.get(0), new Text.Converting(1));
    texts.put(versions.get(1), new Text.Waiting(2));
    texts.put(versions.get(2), new Text.Waiting(0));

    var progress = Progress.of(AVSEC, prepared(texts), Instant.EPOCH);

    assertThat(progress.waiting()).isNull();
    assertThat(progress.running()).hasSize(2);
    assertThat(progress.running().get(0))
        .startsWith("Text 1 of " + versions.size())
        .endsWith(" · converting");
    assertThat(progress.running().get(1))
        .startsWith("Text 2 of " + versions.size())
        .endsWith(" · attempt 2 didn't work; trying again shortly");
  }

  @Test
  void saysTheActWaitsItsTurnOnlyWhileNothingOfItWasTried() {
    var versions = AVSEC.timeline().in(Language.ENG).orElseThrow().versions();
    var texts = new LinkedHashMap<Version, Text>();
    versions.forEach(version -> texts.put(version, new Text.Waiting(0)));

    assertThat(Progress.of(AVSEC, prepared(texts), Instant.EPOCH).waiting()).isNotNull();

    texts.put(versions.get(0), new Text.Waiting(1));

    assertThat(Progress.of(AVSEC, prepared(texts), Instant.EPOCH).waiting()).isNull();
  }

  private static Preparation prepared(SequencedMap<Version, Text> texts) {
    return new Preparation(AVSEC.id(), Language.ENG, Instant.EPOCH, null, texts, List.of(), 0);
  }

  @Test
  void countsTheDatesOfTheAmendedProvisions() {
    assertThat(Progress.dates(386)).isEqualTo("386 dates, one for each amended provision");
    assertThat(Progress.dates(1)).isEqualTo("1 date, for the one amended provision");
    assertThat(Progress.dates(0)).isEqualTo("None: no act has amended its provisions");
  }

  @Test
  void saysPlainlyHowManyVersionsOfOtherActsAreAheadRoundingPastTen() {
    assertThat(Progress.waiting(43))
        .isEqualTo(
            "About 40 texts of other acts are ahead. Nothing's wrong; this one starts by"
                + " itself.");
    assertThat(Progress.waiting(3)).startsWith("3 texts of other acts are ahead.");
    assertThat(Progress.waiting(1)).startsWith("1 text of another act is ahead.");
    assertThat(Progress.waiting(0)).isEqualTo("Nothing's wrong; this one starts by itself.");
  }

  @Test
  void writesDateSourceNotesNextToEachKnownDate() {
    var clause =
        new ActDate.Clause(
            ActDate.Anchor.PUBLICATION,
            1,
            List.of(new ActDate.Provision(ActDate.Provision.Type.ARTICLE, "5")),
            List.of(),
            List.of(),
            "DATPUB +1 V ART 5");
    var dated =
        new Act(
            AVSEC.id(),
            AVSEC.eli(),
            AVSEC.type(),
            AVSEC.titles(),
            AVSEC.dateOfDocument(),
            true,
            null,
            List.of(),
            new Timeline(
                List.of(
                    new Version(
                        Version.Kind.ORIGINAL,
                        LocalDate.of(2020, 7, 2),
                        AVSEC.id().celex(),
                        false,
                        List.of(),
                        AVSEC.timeline().first().languages(),
                        AVSEC.timeline().first().formex()))),
            new ActDates(
                AVSEC.id(),
                List.of(
                    new ActDate(
                        LocalDate.of(2020, 7, 2),
                        ActDate.Kind.ENTRY_INTO_FORCE,
                        false,
                        clause,
                        ActDate.Source.CLAUSE_AND_JOURNAL),
                    new ActDate(
                        LocalDate.of(2020, 8, 1),
                        ActDate.Kind.APPLICATION,
                        false,
                        clause,
                        ActDate.Source.TEXT),
                    new ActDate(
                        LocalDate.of(2020, 9, 1),
                        ActDate.Kind.TAKING_EFFECT,
                        false,
                        clause,
                        ActDate.Source.NOT_CONFIRMED)),
                Map.of()),
            List.of(),
            AVSEC.publications());
    var prep =
        new Preparation(
            dated.id(), Language.ENG, Instant.EPOCH, null, new LinkedHashMap<>(), List.of(), 0);

    var view = PreparingView.of(dated, prep, "asked", "/self", Instant.EPOCH);

    assertThat(view.known())
        .contains(
            new Fact("In force", "from 2 July 2020 (from the Official Journal), Article 5"),
            new Fact("Applies", "from 1 August 2020 (from the enacted text), Article 5"),
            new Fact("Takes effect", "from 1 September 2020 (not confirmed), Article 5"));
  }
}
