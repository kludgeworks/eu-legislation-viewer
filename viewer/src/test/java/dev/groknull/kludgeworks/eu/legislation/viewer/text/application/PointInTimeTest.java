// SPDX-License-Identifier: MIT
package dev.groknull.kludgeworks.eu.legislation.viewer.text.application;

import static org.assertj.core.api.Assertions.assertThat;

import dev.groknull.kludgeworks.eu.legislation.viewer.acts.Act;
import dev.groknull.kludgeworks.eu.legislation.viewer.acts.ActIdentifier;
import dev.groknull.kludgeworks.eu.legislation.viewer.acts.Acts;
import dev.groknull.kludgeworks.eu.legislation.viewer.acts.Eli;
import dev.groknull.kludgeworks.eu.legislation.viewer.acts.Language;
import dev.groknull.kludgeworks.eu.legislation.viewer.acts.ResourceType;
import dev.groknull.kludgeworks.eu.legislation.viewer.acts.Timeline;
import dev.groknull.kludgeworks.eu.legislation.viewer.acts.Version;
import dev.groknull.kludgeworks.eu.legislation.viewer.text.LegalText;
import dev.groknull.kludgeworks.eu.legislation.viewer.text.LegalTextIdentifier;
import dev.groknull.kludgeworks.eu.legislation.viewer.text.LegalTexts;
import dev.groknull.kludgeworks.eu.legislation.viewer.text.Source;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

/**
 * The point-in-time view: the version acts chooses for the date, and that version's text; on
 * 2015/1998 with its original text and one consolidated version, of 22 March 2026. The work runs on
 * the calling thread here, so each future is complete when returned.
 */
class PointInTimeTest {
  static final ActIdentifier AVSEC = new ActIdentifier("32015R1998");
  static final Version ORIGINAL = Version.original(LocalDate.of(2015, 11, 15), AVSEC);
  static final Version CONSOLIDATED =
      new Version(
          Version.Kind.CONSOLIDATED,
          LocalDate.of(2026, 3, 22),
          "02015R1998-20260322",
          false,
          List.of(new ActIdentifier("32026R0449")));

  final Texts texts = new Texts();
  final PointInTime pointInTime = new PointInTime(new OneAct(), texts, Runnable::run);

  @Test
  void givesTheTextOfTheVersionThatAppliesOnTheDate() {
    assertThat(pointInTime.text(AVSEC, LocalDate.of(2026, 4, 1), Language.ENG).join())
        .hasValueSatisfying(
            text -> assertThat(text.source().celex()).isEqualTo("02015R1998-20260322"));
    assertThat(pointInTime.text(AVSEC, LocalDate.of(2026, 3, 21), Language.ENG).join())
        .hasValueSatisfying(text -> assertThat(text.source().celex()).isEqualTo("32015R1998"));
    assertThat(texts.asked)
        .containsExactly(
            LegalTextIdentifier.of(CONSOLIDATED, Language.ENG),
            LegalTextIdentifier.of(ORIGINAL, Language.ENG));
  }

  @Test
  void hasNoTextBeforeTheActsTextAppliesOrForAnotherAct() {
    assertThat(pointInTime.text(AVSEC, LocalDate.of(2015, 1, 1), Language.ENG).join()).isEmpty();
    assertThat(
            pointInTime
                .text(new ActIdentifier("32010R0185"), LocalDate.of(2026, 4, 1), Language.ENG)
                .join())
        .isEmpty();
    assertThat(texts.asked).isEmpty();
  }

  @Test
  void hasNoTextInALanguageTheVersionHasNone() {
    assertThat(pointInTime.text(AVSEC, LocalDate.of(2026, 4, 1), Language.GLE).join()).isEmpty();
  }

  /** 2015/1998 and its two versions; no other act. */
  static final class OneAct implements Acts {
    @Override
    public Optional<Act> find(ActIdentifier id) {
      return Optional.of(
              new Act(
                  AVSEC,
                  new Eli("reg_impl", 2015, "1998"),
                  new ResourceType("REG_IMPL"),
                  LocalDate.of(2015, 11, 5),
                  true,
                  null,
                  new Timeline(List.of(ORIGINAL, CONSOLIDATED))))
          .filter(act -> act.id().equals(id));
    }

    @Override
    public Optional<Act> find(Eli eli) {
      return Optional.empty();
    }
  }

  /** A text in English for every document, carrying only its identity. */
  static final class Texts implements LegalTexts {
    final List<LegalTextIdentifier> asked = new ArrayList<>();

    @Override
    public Optional<LegalText> find(LegalTextIdentifier id) {
      asked.add(id);
      if (id.language() != Language.ENG) {
        return Optional.empty();
      }
      return Optional.of(
          new LegalText(
              AVSEC,
              LegalText.Kind.ORIGINAL,
              null,
              "en",
              "06.02.1",
              "test",
              new Source(id.document(), null, null),
              null,
              null,
              null,
              List.of(),
              null,
              List.of(),
              List.of()));
    }
  }
}
