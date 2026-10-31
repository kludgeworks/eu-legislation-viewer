// SPDX-License-Identifier: MIT
package dev.groknull.kludgeworks.eu.legislation.viewer.acts.infra;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

import dev.groknull.kludgeworks.eu.legislation.viewer.acts.Act;
import dev.groknull.kludgeworks.eu.legislation.viewer.acts.ActIdentifier;
import dev.groknull.kludgeworks.eu.legislation.viewer.acts.Eli;
import dev.groknull.kludgeworks.eu.legislation.viewer.acts.Language;
import dev.groknull.kludgeworks.eu.legislation.viewer.acts.ResourceType;
import dev.groknull.kludgeworks.eu.legislation.viewer.acts.Version;
import dev.groknull.kludgeworks.eu.legislation.viewer.cellar.CellarException;
import dev.groknull.kludgeworks.eu.legislation.viewer.cellar.SparqlResult;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.time.Instant;
import java.time.LocalDate;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import org.junit.jupiter.api.Test;

/** Cellar's answers, recorded (viewer/src/test/resources/acts), folded into acts. */
class CellarActsTest {
  static final ActIdentifier AVSEC = new ActIdentifier("32015R1998");
  static final ActIdentifier REPEALED = new ActIdentifier("32010R0185");
  static final Set<Language> ALL_BUT_IRISH = EnumSet.complementOf(EnumSet.of(Language.GLE));

  @Test
  void readsAnActInForceWithItsVersions() {
    var act = act(AVSEC);

    assertThat(act.eli()).isEqualTo(new Eli("reg_impl", 2015, "1998"));
    assertThat(act.type()).isEqualTo(new ResourceType("REG_IMPL"));
    assertThat(act.dateOfDocument()).isEqualTo(LocalDate.of(2015, 11, 5));
    assertThat(act.inForce()).isTrue();
    assertThat(act.endOfValidity()).isNull();
    var versions = act.timeline().versions();
    assertThat(versions).hasSize(23);
    assertThat(versions.getFirst())
        .isEqualTo(
            Version.original(
                LocalDate.of(2015, 11, 15),
                AVSEC,
                ALL_BUT_IRISH,
                Instant.parse("2015-11-23T07:58:11.083Z")));
    assertThat(versions.get(1).celex()).isEqualTo("02015R1998-20160229");
    assertThat(versions.getLast().celex()).isEqualTo("02015R1998-20261001");
  }

  @Test
  void theOriginalTextAppliesFromTheEntryIntoForceUntilTheFirstConsolidation() {
    var timeline = act(AVSEC).timeline();

    assertThat(timeline.applicableOn(LocalDate.of(2015, 11, 14))).isEmpty();
    assertThat(timeline.applicableOn(LocalDate.of(2016, 2, 1)).orElseThrow().kind())
        .isEqualTo(Version.Kind.ORIGINAL);
    assertThat(timeline.applicableOn(LocalDate.of(2026, 1, 15)).orElseThrow().celex())
        .isEqualTo("02015R1998-20260101");
  }

  @Test
  void aTreatysTextAppliesFromItsEditionNotFromTheTreatysEntryIntoForce() {
    var tfeu = new ActIdentifier("12016E/TXT");
    var work =
        new SparqlResult(
            List.of("eli", "type", "dateOfDocument", "entryIntoForce"),
            List.of(
                new SparqlResult.Row(
                    Map.of(
                        "eli", "http://data.europa.eu/eli/treaty/tfeu_2016/oj",
                        "type",
                            "http://publications.europa.eu/resource/authority/resource-type/TREATY",
                        "dateOfDocument", "2016-06-07",
                        "entryIntoForce", "1958-01-01"))));
    var timeline =
        CellarActs.act(
                tfeu,
                work,
                result(
                    consolidation("02016E/TXT-20151225", "2015-12-25"),
                    consolidation("02016E/TXT-20160901", "2016-09-01")),
                noLanguages())
            .orElseThrow()
            .timeline();

    assertThat(timeline.versions())
        .extracting(Version::celex)
        .containsExactly("02016E/TXT-20151225", "02016E/TXT-20160901");
    assertThat(timeline.applicableOn(LocalDate.of(2010, 1, 1))).isEmpty();
  }

  @Test
  void onlyAVersionPublishedWithoutAPdfIsInterim() {
    var versions = act(AVSEC).timeline().versions();

    assertThat(versions.getLast().interim()).isTrue();
    assertThat(versions.subList(0, versions.size() - 1)).noneMatch(Version::interim);
  }

  @Test
  void aVersionIntegratesTheAmendingActsAndCorrigendaCellarListsButNotOtherConsolidations() {
    var versions = act(AVSEC).timeline().versions();

    assertThat(version(versions, "02015R1998-20260322").integrates())
        .containsExactly(new ActIdentifier("32026R0449"), new ActIdentifier("32026R0449R(01)"));
    // Cellar also lists 02010R0358-20160201, another act's consolidation, here
    assertThat(version(versions, "02015R1998-20190201").integrates())
        .containsExactly(new ActIdentifier("32019R0103"));
  }

  @Test
  void aRepealedActKeepsItsLastVersion() {
    var act = act(REPEALED);

    assertThat(act.inForce()).isFalse();
    assertThat(act.endOfValidity()).isEqualTo(LocalDate.of(2015, 11, 14));
    assertThat(act.timeline().versions()).hasSize(17);
    assertThat(act.timeline().versions().getFirst().date()).isEqualTo(LocalDate.of(2010, 3, 25));
    assertThat(act.versionOn(LocalDate.of(2020, 1, 1)).orElseThrow().celex())
        .isEqualTo("02010R0185-20160201");
  }

  @Test
  void eachVersionIsInTheLanguagesCellarHasItsFormexIn() {
    var versions = act(REPEALED).timeline().versions();
    var withoutCroatian = EnumSet.copyOf(ALL_BUT_IRISH);
    withoutCroatian.remove(Language.HRV);

    // Croatian from Croatia's accession (2013), the original translated into it then
    assertThat(versions.getFirst().languages()).isEqualTo(ALL_BUT_IRISH);
    assertThat(version(versions, "02010R0185-20140701").languages()).isEqualTo(withoutCroatian);
    assertThat(version(versions, "02010R0185-20150301").languages()).isEqualTo(ALL_BUT_IRISH);
    assertThat(act(AVSEC).timeline().versions())
        .allSatisfy(v -> assertThat(v.languages()).isEqualTo(ALL_BUT_IRISH));
  }

  @Test
  void eachVersionHasWhenCellarLastModifiedItsFormex() {
    var versions = act(AVSEC).timeline().versions();

    // republished more than a year after its consolidation
    assertThat(version(versions, "02015R1998-20230401").formexModified())
        .isEqualTo(Instant.parse("2024-06-11T14:53:17.343Z"));
  }

  @Test
  void aVersionCellarHasNoFormexOfIsInNoLanguage() {
    var timeline =
        CellarActs.act(
                AVSEC,
                work(Map.of()),
                result(consolidation("02015R1998-20260322", "2026-03-22")),
                languages(
                    new SparqlResult.Row(
                        Map.of(
                            "version",
                            "32015R1998",
                            "languages",
                            "DEU ENG NOR",
                            "formexModified",
                            "2015-11-23T08:58:11.083+01:00"))))
            .orElseThrow()
            .timeline();

    // Norwegian, an EEA language, isn't an official one
    assertThat(timeline.first().languages()).containsExactly(Language.DEU, Language.ENG);
    assertThat(timeline.latest().languages()).isEmpty();
    assertThat(timeline.latest().formexModified()).isNull();
  }

  @Test
  void anActCellarDoesNotKnowIsNone() {
    var none = new SparqlResult(List.of("eli"), List.of());

    assertThat(CellarActs.act(AVSEC, none, none, none)).isEmpty();
  }

  @Test
  void identifiesAnActByItsEli() {
    var eli = new Eli("reg_impl", 2015, "1998");

    assertThat(CellarActs.identity(eli, recorded("act-by-eli-reg_impl-2015-1998.json")))
        .contains(AVSEC);
  }

  @Test
  void reportsVersionsTheTimelineRefusesAsCellarsData() {
    var twoOnOneDay =
        result(
            consolidation("02015R1998-20260322", "2026-03-22"),
            consolidation("02015R1998-20260322-BIS", "2026-03-22"));

    assertThatExceptionOfType(CellarException.class)
        .isThrownBy(() -> CellarActs.act(AVSEC, work(Map.of()), twoOnOneDay, noLanguages()))
        .withMessageStartingWith("Cellar's data for 32015R1998: Two versions on one day")
        .withCauseInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void reportsAnActWithTwoElisAsCellarsData() {
    var twoElis =
        new SparqlResult(
            List.of("eli", "type", "entryIntoForce"),
            List.of(
                workRow("http://data.europa.eu/eli/reg_impl/2015/1998/oj"),
                workRow("http://data.europa.eu/eli/reg_impl/2015/1999/oj")));

    assertThatExceptionOfType(CellarException.class)
        .isThrownBy(() -> CellarActs.act(AVSEC, twoElis, result(), noLanguages()))
        .withMessageStartingWith("Cellar's data for 32015R1998: several ELIs");
  }

  @Test
  void readsAnActWithAnEliOfAnUnknownShapeWithoutIt() {
    var treaty =
        new SparqlResult(
            List.of("eli", "type", "entryIntoForce"),
            List.of(workRow("http://data.europa.eu/eli/treaty/tfeu_2008/art_251/oj")));

    assertThat(CellarActs.act(AVSEC, treaty, result(), noLanguages()))
        .hasValueSatisfying(act -> assertThat(act.eli()).isNull());
  }

  @Test
  void reportsAnEliOfTwoActsAsCellarsData() {
    var eli = new Eli("reg_impl", 2015, "1998");
    var twoActs =
        new SparqlResult(
            List.of("celex"),
            List.of(
                new SparqlResult.Row(Map.of("celex", "32015R1998")),
                new SparqlResult.Row(Map.of("celex", "32015R1999"))));

    assertThatExceptionOfType(CellarException.class)
        .isThrownBy(() -> CellarActs.identity(eli, twoActs))
        .withMessageStartingWith("Cellar's data for the ELI reg_impl/2015/1998: several acts");
  }

  /** act.rq's answer: one row for 2015/1998, with {@code more} values. */
  private static SparqlResult work(Map<String, String> more) {
    var values = new HashMap<>(more);
    values.putAll(workRow("http://data.europa.eu/eli/reg_impl/2015/1998/oj").values());
    return new SparqlResult(List.copyOf(values.keySet()), List.of(new SparqlResult.Row(values)));
  }

  private static SparqlResult.Row workRow(String eli) {
    return new SparqlResult.Row(
        Map.of(
            "eli",
            eli,
            "type",
            "http://publications.europa.eu/resource/authority/resource-type/REG_IMPL",
            "entryIntoForce",
            "2015-11-15"));
  }

  /** versions.rq's answer: one row per consolidation. */
  private static SparqlResult result(SparqlResult.Row... rows) {
    return new SparqlResult(List.of("version", "date", "pdf", "listed"), List.of(rows));
  }

  /** languages.rq's answer: one row per version with Formex. */
  private static SparqlResult languages(SparqlResult.Row... rows) {
    return new SparqlResult(List.of("version", "languages"), List.of(rows));
  }

  private static SparqlResult noLanguages() {
    return languages();
  }

  private static SparqlResult.Row consolidation(String celex, String date) {
    return new SparqlResult.Row(Map.of("version", celex, "date", date, "pdf", "1"));
  }

  private static Act act(ActIdentifier id) {
    return CellarActs.act(
            id,
            recorded("act-" + id.celex() + ".json"),
            recorded("versions-" + id.celex() + ".json"),
            recorded("languages-" + id.celex() + ".json"))
        .orElseThrow();
  }

  private static Version version(List<Version> versions, String celex) {
    return versions.stream().filter(v -> v.celex().equals(celex)).findFirst().orElseThrow();
  }

  private static SparqlResult recorded(String name) {
    var resource = "/acts/" + name;
    try (var in =
        Objects.requireNonNull(CellarActsTest.class.getResourceAsStream(resource), resource)) {
      return SparqlResult.parse(in.readAllBytes());
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }
}
