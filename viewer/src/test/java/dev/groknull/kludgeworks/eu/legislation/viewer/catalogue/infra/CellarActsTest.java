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
import dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.Publication;
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
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;

/**
 * Tests how {@link CellarActs} folds Cellar's answers into acts. The answers are recorded in {@code
 * viewer/src/test/resources/acts}.
 */
class CellarActsTest {
  static final ActIdentifier AVSEC = new ActIdentifier("32015R1998");
  static final ActIdentifier REPEALED = new ActIdentifier("32010R0185");
  static final Set<Language> ALL_BUT_IRISH = EnumSet.complementOf(EnumSet.of(Language.GLE));

  @Test
  void readsHowItAndTheActsItsVersionsIntegrateWerePublished() {
    var act = act(AVSEC);
    // The act itself and the acts its versions integrate, corrigenda included. No consolidation.
    assertThat(act.publications()).hasSize(act.integratedBetween(first(act), last(act)).size() + 1);
    assertThat(act.publication(new ActIdentifier("32026R0449")))
        .hasValueSatisfying(
            published -> {
              assertThat(published.eli()).isEqualTo(new Eli("reg_impl", 2026, "449"));
              assertThat(published.title(Language.ENG))
                  .hasValueSatisfying(
                      title ->
                          assertThat(title)
                              .startsWith("Commission Implementing Regulation (EU) 2026/449"));
            });
    assertThat(reference(act, "32026R0449")).hasValue("OJ L, 2026/449, 2.3.2026");
    assertThat(reference(act, "32018R0055")).hasValue("OJ L 10, 13.1.2018");
    assertThat(reference(act, "32019R0103")).hasValue("OJ L 21, 24.1.2019");
    assertThat(reference(act, AVSEC.celex())).hasValue("OJ L 299, 14.11.2015");
  }

  private static Optional<String> reference(Act act, String celex) {
    return act.publication(new ActIdentifier(celex))
        .map(Publication::journal)
        .map(Publication.Journal::reference);
  }

  private static Version first(Act act) {
    return act.timeline().versions().getFirst();
  }

  private static Version last(Act act) {
    return act.timeline().versions().getLast();
  }

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
                noAmendments(),
                noPublications())
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
    // The public consolidation lists only the base act, but its production version lists 2018/55.
    assertThat(version(versions, "02015R1998-20180206").integrates())
        .containsExactly(new ActIdentifier("32018R0055"));
    // Cellar also lists 02010R0358-20160201 here. That is a consolidation of another act.
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
    assertThat(act.versionOn(LocalDate.of(2020, 1, 1), Language.ENG).orElseThrow().celex())
        .isEqualTo("02010R0185-20160201");
  }

  @Test
  void eachVersionIsInTheLanguagesCellarHasItsFormexIn() {
    var versions = act(REPEALED).timeline().versions();
    var withoutCroatian = EnumSet.copyOf(ALL_BUT_IRISH);
    withoutCroatian.remove(Language.HRV);

    // Croatian starts at Croatia's accession in 2013. The original was translated into it then.
    assertThat(versions.getFirst().languages()).isEqualTo(ALL_BUT_IRISH);
    assertThat(version(versions, "02010R0185-20140701").languages()).isEqualTo(withoutCroatian);
    assertThat(version(versions, "02010R0185-20150301").languages()).isEqualTo(ALL_BUT_IRISH);
    assertThat(act(AVSEC).timeline().versions())
        .allSatisfy(v -> assertThat(v.languages()).isEqualTo(ALL_BUT_IRISH));
  }

  @Test
  void eachVersionHasWhenCellarLastModifiedItsFormex() {
    var versions = act(AVSEC).timeline().versions();

    // Cellar republished it more than a year after its consolidation.
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
                noAmendments(),
                noPublications())
            .orElseThrow()
            .timeline();

    // Norwegian is an EEA language, but not an official one.
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

    assertThat(dates).hasSize(18).doesNotContainKey(AVSEC);
    assertThat(dates.getOrDefault(new ActIdentifier("32018R0055"), List.of()))
        .extracting(ActDate::date, ActDate::kind)
        .containsExactly(
            tuple(LocalDate.of(2018, 2, 2), Kind.ENTRY_INTO_FORCE),
            tuple(LocalDate.of(2018, 2, 6), Kind.APPLICATION));
    // It applies before it enters into force, as its Article 2 says.
    assertThat(dates.getOrDefault(new ActIdentifier("32017R0815"), List.of()))
        .extracting(ActDate::date, ActDate::kind)
        .containsExactly(
            tuple(LocalDate.of(2017, 6, 1), Kind.APPLICATION),
            tuple(LocalDate.of(2017, 6, 2), Kind.ENTRY_INTO_FORCE));
  }

  @Test
  void correctsADateCountedFromPublicationAgainstThePublicationDate() {
    var act = act(AVSEC);
    var date = act.datesOf(new ActIdentifier("32020R0910")).getFirst();

    // Published 1 July 2020, DATPUB +1: clause sets 2 July 2020; Cellar recorded 6 July 2020.
    assertThat(date.date()).isEqualTo(LocalDate.of(2020, 7, 2));
    assertThat(date.kind()).isEqualTo(Kind.ENTRY_INTO_FORCE);
    assertThat(date.clause().text()).contains("DATPUB +1");
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
    // A day that does not exist gives no date. A date of an unknown type is left out. A
    // consolidation is not an act.
    assertThat(dates.getOrDefault(new ActIdentifier("32015R2426"), List.of()))
        .singleElement()
        .extracting(ActDate::date, ActDate::kind)
        .containsExactly(null, Kind.APPLICATION);
    assertThat(dates).hasSize(4);
  }

  @Test
  void checkAgainstPublicationLeavesDateUnchangedWhenMatchingPublication() {
    var actId = new ActIdentifier("32015R1998");
    var clause =
        new Clause(
            Anchor.PUBLICATION,
            1,
            List.of(new Provision(Provision.Type.ARTICLE, "4")),
            List.of(),
            "DATPUB +1 V ART 4");
    var date = new ActDate(LocalDate.of(2015, 11, 15), Kind.ENTRY_INTO_FORCE, false, clause);
    var publication =
        new Publication(
            null, Map.of(), new Publication.Journal("L", null, 299, LocalDate.of(2015, 11, 14)));

    var checked =
        CellarDates.checkAgainstPublication(
            Map.of(actId, List.of(date)), Map.of(actId, publication));

    assertThat(checked.get(actId))
        .singleElement()
        .satisfies(d -> assertThat(d.date()).isEqualTo(LocalDate.of(2015, 11, 15)));
  }

  @Test
  void checkAgainstPublicationAdoptsDateFromClauseOnMismatch() {
    var actId = new ActIdentifier("32020R0910");
    var clause =
        new Clause(
            Anchor.PUBLICATION,
            1,
            List.of(new Provision(Provision.Type.ARTICLE, "5")),
            List.of(),
            "DATPUB +1 V ART 5");
    var wrongDate = new ActDate(LocalDate.of(2020, 7, 6), Kind.ENTRY_INTO_FORCE, false, clause);
    var publication =
        new Publication(
            null, Map.of(), new Publication.Journal("L", null, 208, LocalDate.of(2020, 7, 1)));

    var checked =
        CellarDates.checkAgainstPublication(
            Map.of(actId, List.of(wrongDate)), Map.of(actId, publication));

    assertThat(checked.get(actId))
        .singleElement()
        .satisfies(
            d -> {
              assertThat(d.date()).isEqualTo(LocalDate.of(2020, 7, 2));
              assertThat(d.clause()).isEqualTo(clause);
            });
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
                    noAmendments(),
                    noPublications()))
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
    // Cellar has no day for the amendments by 2019/413.
    assertThat(amendments)
        .filteredOn(amendment -> amendment.act().celex().equals("32019R0413"))
        .extracting(Amendment::from)
        .containsOnlyNulls();
    // 2017/837 corrects the Swedish and the Polish texts only.
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

    // A deletion is found at the unit of the earlier text. An addition is found at the unit of the
    // later text.
    assertThat(amended.of("anx/pnt_6.8.1.8", true))
        .extracting(Amendment::act, Amendment::role)
        .containsExactly(tuple(new ActIdentifier("32026R0449"), Role.DELETION));
    assertThat(amended.of("anx/pnt_8.1.4.8", true))
        .extracting(Amendment::role)
        .containsExactly(Role.ADDITION);
    // Point 6.8.1.8 is numbered under 6.8.1, but legal-text keeps it beside 6.8.1. Its deletion
    // counts for 6.8.1 when 6.8.1 is deleted whole. It does not count when only the own words of
    // 6.8.1 changed.
    assertThat(amended.of("anx/pnt_6.8.1", true))
        .extracting(Amendment::role)
        .contains(Role.DELETION);
    assertThat(amended.of("anx/pnt_6.8.1", false)).isEmpty();
    // 2026/449 replaces it from 1 October 2026. That is after the later version.
    assertThat(amended.of("anx/pnt_8.1.2.3/pnt_c", false)).isEmpty();
  }

  @Test
  void findsNoAmendmentOfALanguageVersionInAnother() {
    var act = act(AVSEC);
    var units = List.of("anx", "anx/pnt_8.3.1");

    var from = version(act.timeline().versions(), "02015R1998-20160229");
    var to = version(act.timeline().versions(), "02015R1998-20170607");

    assertThat(act.amended(from, to, units, units, Language.ENG).of("anx/pnt_8.3.1", false))
        .isEmpty();
    assertThat(act.amended(from, to, units, units, Language.SWE).of("anx/pnt_8.3.1", false))
        .extracting(amendment -> amendment.act().celex())
        .containsExactly("32017R0837");
  }

  @Test
  void anActCellarDoesNotKnowIsNone() {
    var none = new SparqlResult(List.of("eli"), List.of());

    assertThat(CellarActs.act(AVSEC, none, none, none, none, none, none, none)).isEmpty();
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
                    noAmendments(),
                    noPublications()))
        .withMessageStartingWith("Cellar's data for 32015R1998: Two versions on one day")
        .withCauseInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void keepsOriginalTextForLanguagesWithoutSameDayConsolidation() {
    var sameDayConsolidation = result(consolidation("02015R1998-20151115", "2015-11-15"));
    var languages =
        languages(
            new SparqlResult.Row(
                Map.of(
                    "version",
                    "32015R1998",
                    "languages",
                    "ENG FRA DEU",
                    "formexModified",
                    "2015-11-23T07:58:11.083Z")),
            new SparqlResult.Row(
                Map.of(
                    "version",
                    "02015R1998-20151115",
                    "languages",
                    "DEU",
                    "formexModified",
                    "2015-11-23T07:58:11.083Z")));

    var timeline =
        CellarActs.act(
                AVSEC,
                work(Map.of()),
                sameDayConsolidation,
                languages,
                noDates(),
                noTitles(),
                noAmendments(),
                noPublications())
            .orElseThrow()
            .timeline();

    assertThat(timeline.versions()).hasSize(2);

    var english = timeline.in(Language.ENG).orElseThrow();
    assertThat(english.versions()).hasSize(1);
    assertThat(english.first().celex()).isEqualTo("32015R1998");
    assertThat(english.first().kind()).isEqualTo(Version.Kind.ORIGINAL);

    var german = timeline.in(Language.DEU).orElseThrow();
    assertThat(german.versions()).hasSize(1);
    assertThat(german.first().celex()).isEqualTo("02015R1998-20151115");
    assertThat(german.first().kind()).isEqualTo(Version.Kind.CONSOLIDATED);
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
                    AVSEC,
                    twoElis,
                    result(),
                    noLanguages(),
                    noDates(),
                    noTitles(),
                    noAmendments(),
                    noPublications()))
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
                AVSEC,
                treaty,
                result(),
                noLanguages(),
                noDates(),
                noTitles(),
                noAmendments(),
                noPublications()))
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
                AVSEC,
                work,
                result(),
                noLanguages(),
                noDates(),
                noTitles(),
                noAmendments(),
                noPublications()))
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

  /** publications.rq's answer, with no publications. */
  private static SparqlResult noPublications() {
    return new SparqlResult(List.of("subject", "eli", "code", "title"), List.of());
  }

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
