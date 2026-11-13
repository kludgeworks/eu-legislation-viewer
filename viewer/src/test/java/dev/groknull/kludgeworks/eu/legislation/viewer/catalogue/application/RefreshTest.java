// SPDX-License-Identifier: MIT
package dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.application;

import static org.assertj.core.api.Assertions.assertThat;

import dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.Act;
import dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.ActIdentifier;
import dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.Amendment;
import dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.AmendmentsChanged;
import dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.LanguageAdded;
import dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.Location;
import dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.OneActCellar;
import dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.Version;
import dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.VersionAdded;
import dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.VersionRepublished;
import dev.groknull.kludgeworks.eu.legislation.viewer.content.VersionWithdrawn;
import dev.groknull.kludgeworks.eu.legislation.viewer.language.Language;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.UnaryOperator;
import org.junit.jupiter.api.Test;

/**
 * The comparison of a held act as stored with the act as Cellar has it now, on 2015/1998 as {@link
 * OneActCellar} has it: one event for each difference, and a version withdrawn only when two reads
 * at least 30 minutes apart do not have it.
 */
class RefreshTest {
  private static final Act STORED = OneActCellar.ACT;
  private static final ActIdentifier ACT = STORED.id();
  private static final String MARCH_2026 = "02015R1998-20260322";
  private static final Duration WITHDRAWN_AFTER = Duration.ofMinutes(30);
  private static final Instant NOW = Instant.parse("2026-11-12T10:00:00Z");

  @Test
  void nothingChangedPublishesNothing() {
    var refresh = Refresh.of(STORED, Map.of(), STORED, NOW, WITHDRAWN_AFTER);

    assertThat(refresh.events()).isEmpty();
    assertThat(refresh.missing()).isEmpty();
    assertThat(refresh.withdrawn()).isEmpty();
    assertThat(refresh.recheckAt()).isNull();
  }

  @Test
  void aNewVersionIsAddedInTheLanguagesItHas() {
    var refresh = Refresh.of(STORED, Map.of(), OneActCellar.REFRESHED, NOW, WITHDRAWN_AFTER);

    assertThat(refresh.events())
        .containsExactly(
            new VersionAdded(
                ACT,
                "02015R1998-20220301",
                LocalDate.of(2022, 3, 1),
                Set.of(Language.ENG, Language.FRA)));
  }

  @Test
  void aVersionInANewLanguageHasThatLanguageAdded() {
    var read =
        change(
            version -> {
              var formex = new EnumMap<>(version.formex());
              formex.put(Language.DEU, formex.get(Language.ENG));
              return new Version(
                  version.kind(),
                  version.date(),
                  version.celex(),
                  version.interim(),
                  version.integrates(),
                  formex.keySet(),
                  formex);
            });

    assertThat(Refresh.of(STORED, Map.of(), read, NOW, WITHDRAWN_AFTER).events())
        .containsExactly(new LanguageAdded(ACT, MARCH_2026, Language.DEU));
  }

  @Test
  void aVersionWhoseFormexChangedInALanguageIsRepublishedInIt() {
    var republished = Instant.parse("2026-11-04T08:54:27Z");
    var read =
        change(
            version -> {
              var formex = new EnumMap<>(version.formex());
              formex.put(Language.ENG, new Version.Formex("a new fixity", republished));
              return withFormex(version, formex);
            });

    assertThat(Refresh.of(STORED, Map.of(), read, NOW, WITHDRAWN_AFTER).events())
        .containsExactly(new VersionRepublished(ACT, MARCH_2026, Language.ENG, republished));
  }

  @Test
  void aChangeOfMetadataAloneIsNoRepublication() {
    // Cellar modified the manifestation, but its files are the same.
    var read =
        change(
            version -> {
              var formex = new EnumMap<Language, Version.Formex>(Language.class);
              version
                  .formex()
                  .forEach(
                      (language, each) ->
                          formex.put(language, new Version.Formex(each.fixity(), NOW)));
              return withFormex(version, formex);
            });

    assertThat(Refresh.of(STORED, Map.of(), read, NOW, WITHDRAWN_AFTER).events()).isEmpty();
  }

  @Test
  void aFormexThatGainsAFixityIsRepublished() {
    // Last modified before 2024, when Cellar kept no fixities, and republished since.
    var stored =
        change(
            STORED,
            version ->
                withFormex(version, without(version, Instant.parse("2017-04-25T10:42:16Z"))));

    assertThat(Refresh.of(stored, Map.of(), STORED, NOW, WITHDRAWN_AFTER).events())
        .containsExactlyElementsOf(
            STORED.timeline().versions().stream()
                .filter(each -> each.celex().equals(MARCH_2026))
                .flatMap(
                    each ->
                        each.formex().entrySet().stream()
                            .map(
                                formex ->
                                    new VersionRepublished(
                                        ACT,
                                        MARCH_2026,
                                        formex.getKey(),
                                        formex.getValue().modified())))
                .toList());
  }

  @Test
  void aFormexWithoutAFixityThatIsModifiedIsNoRepublication() {
    // Cellar changed its metadata only: it has no fixity, and gains none.
    var stored =
        change(
            STORED,
            version ->
                withFormex(version, without(version, Instant.parse("2017-04-25T10:42:16Z"))));
    var read = change(STORED, version -> withFormex(version, without(version, NOW)));

    assertThat(Refresh.of(stored, Map.of(), read, NOW, WITHDRAWN_AFTER).events()).isEmpty();
  }

  @Test
  void aFormexNotStoredYetIsStoredAsItIs() {
    // Stored before the store kept the Formex of each language.
    var stored = change(STORED, version -> withFormex(version, Map.of()));

    assertThat(Refresh.of(stored, Map.of(), STORED, NOW, WITHDRAWN_AFTER).events()).isEmpty();
  }

  @Test
  void aVersionThatOneReadMissesIsNotWithdrawnButReadAgain() {
    var read = without(MARCH_2026);

    var refresh = Refresh.of(STORED, Map.of(), read, NOW, WITHDRAWN_AFTER);

    assertThat(refresh.events()).isEmpty();
    assertThat(refresh.withdrawn()).isEmpty();
    assertThat(refresh.missing()).containsExactly(Map.entry(MARCH_2026, NOW));
    assertThat(refresh.recheckAt()).isEqualTo(NOW.plus(WITHDRAWN_AFTER));
  }

  @Test
  void aVersionThatTwoReadsTooCloseTogetherMissIsStillMissing() {
    var first = NOW.minus(Duration.ofMinutes(29));

    var refresh =
        Refresh.of(STORED, Map.of(MARCH_2026, first), without(MARCH_2026), NOW, WITHDRAWN_AFTER);

    assertThat(refresh.withdrawn()).isEmpty();
    assertThat(refresh.missing()).containsExactly(Map.entry(MARCH_2026, first));
    assertThat(refresh.recheckAt()).isEqualTo(first.plus(WITHDRAWN_AFTER));
  }

  @Test
  void aVersionThatTwoReadsHalfAnHourApartMissIsWithdrawn() {
    var first = NOW.minus(WITHDRAWN_AFTER);

    var refresh =
        Refresh.of(STORED, Map.of(MARCH_2026, first), without(MARCH_2026), NOW, WITHDRAWN_AFTER);

    assertThat(refresh.events()).containsExactly(new VersionWithdrawn(MARCH_2026));
    assertThat(refresh.withdrawn()).containsExactly(MARCH_2026);
    assertThat(refresh.missing()).isEmpty();
    assertThat(refresh.recheckAt()).isNull();
  }

  @Test
  void aMissingVersionThatComesBackIsNoLongerMissing() {
    var refresh =
        Refresh.of(
            STORED,
            Map.of(MARCH_2026, NOW.minus(Duration.ofHours(1))),
            STORED,
            NOW,
            WITHDRAWN_AFTER);

    assertThat(refresh.events()).isEmpty();
    assertThat(refresh.missing()).isEmpty();
    assertThat(refresh.withdrawn()).isEmpty();
  }

  @Test
  void newAmendmentsChangeTheAmendments() {
    var read =
        new Act(
            STORED.id(),
            STORED.eli(),
            STORED.type(),
            STORED.titles(),
            STORED.dateOfDocument(),
            STORED.inForce(),
            STORED.endOfValidity(),
            STORED.timeline(),
            STORED.dates(),
            STORED.amendingDates(),
            List.of(
                new Amendment(
                    new ActIdentifier("32026R1972"),
                    new Location("AR 3"),
                    Amendment.Role.AMENDMENT,
                    LocalDate.of(2026, 12, 2),
                    null,
                    Set.of())),
            STORED.publications());

    assertThat(Refresh.of(STORED, Map.of(), read, NOW, WITHDRAWN_AFTER).events())
        .containsExactly(new AmendmentsChanged(ACT));
  }

  /** Returns 2015/1998 with the consolidation of 22 March 2026 changed by {@code change}. */
  private static Act change(UnaryOperator<Version> change) {
    return change(STORED, change);
  }

  private static Act change(Act act, UnaryOperator<Version> change) {
    return OneActCellar.with(
        act.timeline().versions().stream()
            .map(each -> each.celex().equals(MARCH_2026) ? change.apply(each) : each)
            .toList());
  }

  /** Returns 2015/1998 without the version {@code celex}. */
  private static Act without(String celex) {
    return OneActCellar.with(
        STORED.timeline().versions().stream().filter(each -> !each.celex().equals(celex)).toList());
  }

  /** Returns the Formex of {@code version} without fixities, modified at {@code modified}. */
  private static Map<Language, Version.Formex> without(Version version, Instant modified) {
    var formex = new EnumMap<Language, Version.Formex>(Language.class);
    version
        .languages()
        .forEach(language -> formex.put(language, new Version.Formex(null, modified)));
    return formex;
  }

  private static Version withFormex(Version version, Map<Language, Version.Formex> formex) {
    return new Version(
        version.kind(),
        version.date(),
        version.celex(),
        version.interim(),
        version.integrates(),
        version.languages(),
        formex);
  }
}
