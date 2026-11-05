// SPDX-License-Identifier: MIT
package dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.infra;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.assertj.core.api.Assertions.tuple;

import dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.Act;
import dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.ActDate;
import dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.ActDate.Anchor;
import dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.ActDate.Clause;
import dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.ActDate.Kind;
import dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.ActDate.Provision;
import dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.ActIdentifier;
import dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.Amendment;
import dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.Amendment.Role;
import dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.Eli;
import dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.ResourceType;
import dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.Version;
import dev.groknull.kludgeworks.eu.legislation.viewer.cellar.CellarException;
import dev.groknull.kludgeworks.eu.legislation.viewer.cellar.SparqlResult;
import dev.groknull.kludgeworks.eu.legislation.viewer.language.Language;
import java.time.Instant;
import java.time.LocalDate;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.stream.Collectors;
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
                noLanguages(),
                noDates(),
                noTitles(),
                noAmendments())
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
                            "2015-11-23T08:58:11.083+01:00"))),
                noDates(),
                noTitles(),
                noAmendments())
            .orElseThrow()
            .timeline();

    // Norwegian, an EEA language, isn't an official one
    assertThat(timeline.first().languages()).containsExactly(Language.DEU, Language.ENG);
    assertThat(timeline.latest().languages()).isEmpty();
    assertThat(timeline.latest().formexModified()).isNull();
  }

  @Test
  void readsTheActsOwnDatesWithTheClausesThatSetThem() {
    assertThat(act(AVSEC).dates())
        .containsExactly(
            new ActDate(
                LocalDate.of(2015, 11, 15),
                Kind.ENTRY_INTO_FORCE,
                false,
                new Clause(
                    Anchor.PUBLICATION, 1, List.of(article("3")), List.of(), "DATPUB +1 V ART 3")),
            new ActDate(
                LocalDate.of(2016, 2, 1),
                Kind.APPLICATION,
                false,
                new Clause(null, null, List.of(article("2")), List.of(), "V ART 2")));
  }

  @Test
  void readsTheDatesOfEachActItsVersionsIntegrate() {
    var dates = act(AVSEC).amendingDates();

    assertThat(dates).hasSize(17).doesNotContainKey(AVSEC);
    // applies before it enters into force, as its Article 2 says
    assertThat(dates.getOrDefault(new ActIdentifier("32017R0815"), List.of()))
        .extracting(ActDate::date, ActDate::kind)
        .containsExactly(
            tuple(LocalDate.of(2017, 6, 1), Kind.APPLICATION),
            tuple(LocalDate.of(2017, 6, 2), Kind.ENTRY_INTO_FORCE));
  }

  @Test
  void aDateALaterActMovedNamesThatAct() {
    var application = act(AVSEC).datesOf(new ActIdentifier("32019R0103")).getLast();

    assertThat(application.date()).isEqualTo(LocalDate.of(2021, 12, 31));
    assertThat(application.partial()).isTrue();
    assertThat(application.clause().provisions()).containsExactly(article("2"));
    assertThat(application.clause().amendedBy()).containsExactly(new ActIdentifier("32020R0910"));
  }

  @Test
  void aPlaceholderDateIsNoDateButKeepsItsClause() {
    var application = act(AVSEC).datesOf(new ActIdentifier("32019R0413")).getFirst();

    assertThat(application.kind()).isEqualTo(Kind.APPLICATION);
    assertThat(application.date()).isNull();
    assertThat(application.clause().provisions()).containsExactly(article("3"));
  }

  @Test
  void onlyTheVersionAnActAppliesInBeforeItIsIncludedLagsTheLaw() {
    var act = act(AVSEC);
    var lagging =
        act.timeline().versions().stream()
            .collect(
                Collectors.toMap(
                    Version::celex, act::notYetConsolidated, (one, other) -> one, TreeMap::new));

    assertThat(lagging).hasSize(23);
    assertThat(lagging.values().stream().filter(acts -> !acts.isEmpty()).count()).isOne();
    assertThat(lagging.get("02015R1998-20160229"))
        .singleElement()
        .satisfies(
            unconsolidated -> {
              assertThat(unconsolidated.act()).isEqualTo(new ActIdentifier("32017R0815"));
              assertThat(unconsolidated.appliesFrom().date()).isEqualTo("2017-06-01");
              assertThat(unconsolidated.includedIn().celex()).isEqualTo("02015R1998-20170607");
            });
  }

  @Test
  void theActLagsTheLawOnlyWhile2017815AppliesUnincluded() {
    assertThat(act(AVSEC).lags())
        .extracting(lag -> lag.act().celex(), lag -> lag.from(), lag -> lag.includedIn().celex())
        .containsExactly(tuple("32017R0815", LocalDate.of(2017, 6, 1), "02015R1998-20170607"));
  }

  @Test
  void readsDatesOfEveryKindAndClauseCellarWrites() {
    var dates =
        CellarDates.byAct(
            AVSEC,
            dates(
                dateRow("32018D1269", "2018-09-20", "PE", "{DATNOT|x} {V|x} {ART|x} 2"),
                dateRow("32021R0255", "2021-03-15", "MA/PART", "V ART 2.2(f) ET 2.2(g)"),
                dateRow("32012R1064", "2012-11-17", "EV", "DATPUB + 3 VOIR ANN"),
                dateRow("32015R2426", "2015-02-29", "MA", "V ART 2"),
                dateRow("32015R2426", "2016-01-11", "ECHLET", ""),
                dateRow("02015R1998-20160229", "2016-02-29", "", "")));

    assertThat(dates.getOrDefault(new ActIdentifier("32018D1269"), List.of()))
        .containsExactly(
            new ActDate(
                LocalDate.of(2018, 9, 20),
                Kind.TAKING_EFFECT,
                false,
                new Clause(
                    Anchor.NOTIFICATION,
                    null,
                    List.of(article("2")),
                    List.of(),
                    "DATNOT V ART 2")));
    assertThat(
            dates
                .getOrDefault(new ActIdentifier("32021R0255"), List.of())
                .getFirst()
                .clause()
                .provisions())
        .containsExactly(article("2.2(f)"), article("2.2(g)"));
    assertThat(dates.getOrDefault(new ActIdentifier("32021R0255"), List.of()).getFirst().partial())
        .isTrue();
    assertThat(dates.getOrDefault(new ActIdentifier("32012R1064"), List.of()).getFirst().clause())
        .extracting(Clause::from, Clause::days, Clause::provisions)
        .containsExactly(Anchor.PUBLICATION, 3, List.of(new Provision(Provision.Type.ANNEX, null)));
    // a day that doesn't exist is none; a type not known is left out; a consolidation isn't an act
    assertThat(dates.getOrDefault(new ActIdentifier("32015R2426"), List.of()))
        .singleElement()
        .extracting(ActDate::date, ActDate::kind)
        .containsExactly(null, Kind.APPLICATION);
    assertThat(dates).hasSize(4);
  }

  @Test
  void readsAnActsTitleInEachLanguage() {
    var act = act(AVSEC);

    assertThat(act.titles()).hasSize(23).doesNotContainKey(Language.GLE);
    assertThat(act.title(Language.ENG))
        .contains(
            "Commission Implementing Regulation (EU) 2015/1998 of 5 November 2015 laying down"
                + " detailed measures for the implementation of the common basic standards on"
                + " aviation security (Text with EEA relevance)");
    assertThat(act.title(Language.FRA))
        .hasValueSatisfying(
            t -> assertThat(t).startsWith("Règlement d'exécution (UE) 2015/1998 de la Commission"));
  }

  @Test
  void reportsTwoTitlesInOneLanguageAsCellarsData() {
    var twice =
        new SparqlResult(
            List.of("code", "title"),
            List.of(
                new SparqlResult.Row(Map.of("code", "ENG", "title", "One")),
                new SparqlResult.Row(Map.of("code", "ENG", "title", "Two"))));

    assertThatExceptionOfType(CellarException.class)
        .isThrownBy(
            () ->
                CellarActs.act(
                    AVSEC,
                    work(Map.of()),
                    result(),
                    noLanguages(),
                    noDates(),
                    twice,
                    noAmendments()))
        .withMessageStartingWith("Cellar's data for 32015R1998: several titles in ENG");
  }

  @Test
  void readsWhatOtherActsDidToItsProvisions() {
    var amendments = act(AVSEC).amendments();

    assertThat(amendments).hasSize(386);
    assertThat(amendments)
        .filteredOn(amendment -> amendment.location().code().equals("AN PO 8.1.2.3 PTA (c)"))
        .extracting(Amendment::act, Amendment::role, Amendment::from)
        .contains(
            tuple(new ActIdentifier("32026R0449"), Role.REPLACEMENT, LocalDate.of(2026, 10, 1)));
    // Cellar has no day for 2019/413's
    assertThat(amendments)
        .filteredOn(amendment -> amendment.act().celex().equals("32019R0413"))
        .extracting(Amendment::from)
        .containsOnlyNulls();
    // 2017/837 corrects the Swedish and the Polish
    assertThat(amendments)
        .filteredOn(amendment -> amendment.act().celex().equals("32017R0837"))
        .flatExtracting(Amendment::languages)
        .containsOnly(Language.SWE, Language.POL);
  }

  @Test
  void findsTheAmendmentsBetweenTwoVersionsAtTheUnitsOfTheirTexts() {
    var act = act(AVSEC);
    var from = version(act.timeline().versions(), "02015R1998-20260101");
    var to = version(act.timeline().versions(), "02015R1998-20260322");

    var amended =
        act.amended(
            from,
            to,
            List.of("anx", "anx/pnt_6.8.1", "anx/pnt_6.8.1.8", "anx/pnt_8.1.2.3/pnt_c"),
            List.of("anx", "anx/pnt_6.8.1", "anx/pnt_8.1.2.3/pnt_c", "anx/pnt_8.1.4.8"),
            Language.ENG);

    // a deletion at the earlier text's unit, an addition at the later's
    assertThat(amended.of("anx/pnt_6.8.1.8"))
        .extracting(Amendment::act, Amendment::role)
        .containsExactly(tuple(new ActIdentifier("32026R0449"), Role.DELETION));
    assertThat(amended.of("anx/pnt_8.1.4.8"))
        .extracting(Amendment::role)
        .containsExactly(Role.ADDITION);
    // the point numbered under it, which legal-text keeps beside it
    assertThat(amended.of("anx/pnt_6.8.1")).extracting(Amendment::role).contains(Role.DELETION);
    // 2026/449 replaces it from 1 October 2026, after the later version
    assertThat(amended.of("anx/pnt_8.1.2.3/pnt_c")).isEmpty();
  }

  @Test
  void findsNoAmendmentOfALanguageVersionInAnother() {
    var act = act(AVSEC);
    var units = List.of("anx", "anx/pnt_8.3.1");

    var from = version(act.timeline().versions(), "02015R1998-20160229");
    var to = version(act.timeline().versions(), "02015R1998-20170607");

    assertThat(act.amended(from, to, units, units, Language.ENG).of("anx/pnt_8.3.1")).isEmpty();
    assertThat(act.amended(from, to, units, units, Language.SWE).of("anx/pnt_8.3.1"))
        .extracting(amendment -> amendment.act().celex())
        .containsExactly("32017R0837");
  }

  @Test
  void anActCellarDoesNotKnowIsNone() {
    var none = new SparqlResult(List.of("eli"), List.of());

    assertThat(CellarActs.act(AVSEC, none, none, none, none, none, none)).isEmpty();
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
        .isThrownBy(
            () ->
                CellarActs.act(
                    AVSEC,
                    work(Map.of()),
                    twoOnOneDay,
                    noLanguages(),
                    noDates(),
                    noTitles(),
                    noAmendments()))
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
        .isThrownBy(
            () ->
                CellarActs.act(
                    AVSEC, twoElis, result(), noLanguages(), noDates(), noTitles(), noAmendments()))
        .withMessageStartingWith("Cellar's data for 32015R1998: several ELIs");
  }

  @Test
  void readsAnActsEliOfAnyTemplate() {
    var treaty =
        new SparqlResult(
            List.of("eli", "type", "entryIntoForce"),
            List.of(workRow("http://data.europa.eu/eli/treaty/tfeu_2008/art_251/oj")));

    assertThat(
            CellarActs.act(
                AVSEC, treaty, result(), noLanguages(), noDates(), noTitles(), noAmendments()))
        .hasValueSatisfying(
            act -> assertThat(act.eli()).isEqualTo(new Eli("treaty/tfeu_2008/art_251")));
  }

  @Test
  void readsAnActWithAnEliThatIsntOneWithoutIt() {
    var work =
        new SparqlResult(
            List.of("eli", "type", "entryIntoForce"), List.of(workRow("ELI:L:2023:2616:OJ")));

    assertThat(
            CellarActs.act(
                AVSEC, work, result(), noLanguages(), noDates(), noTitles(), noAmendments()))
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

  /** dates.rq's answer: one row per date. */
  private static SparqlResult dates(SparqlResult.Row... rows) {
    return new SparqlResult(List.of("subject", "date", "type", "comment"), List.of(rows));
  }

  private static SparqlResult.Row dateRow(
      String subject, String date, String type, String comment) {
    return new SparqlResult.Row(
        Map.of("subject", subject, "date", date, "type", "{" + type + "|x}", "comment", comment));
  }

  private static Provision article(String number) {
    return new Provision(Provision.Type.ARTICLE, number);
  }

  /** titles.rq's answer, with no titles. */
  private static SparqlResult noTitles() {
    return new SparqlResult(List.of("code", "title"), List.of());
  }

  /** amendments.rq's answer, with no amendments. */
  private static SparqlResult noAmendments() {
    return new SparqlResult(
        List.of("subject", "location", "role", "from", "until", "languages"), List.of());
  }

  /** dates.rq's answer, with no dates. */
  private static SparqlResult noDates() {
    return new SparqlResult(List.of("subject", "date", "type", "comment"), List.of());
  }

  private static SparqlResult.Row consolidation(String celex, String date) {
    return new SparqlResult.Row(Map.of("version", celex, "date", date, "pdf", "1"));
  }

  private static Act act(ActIdentifier id) {
    return RecordedActs.act(id);
  }

  private static Version version(List<Version> versions, String celex) {
    return versions.stream().filter(v -> v.celex().equals(celex)).findFirst().orElseThrow();
  }

  private static SparqlResult recorded(String name) {
    return RecordedActs.answer(name);
  }
}
