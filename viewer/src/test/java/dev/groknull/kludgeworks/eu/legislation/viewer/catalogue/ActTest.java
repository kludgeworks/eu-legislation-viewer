// SPDX-License-Identifier: MIT
package dev.groknull.kludgeworks.eu.legislation.viewer.catalogue;

import static org.assertj.core.api.Assertions.assertThat;

import dev.groknull.kludgeworks.eu.legislation.viewer.language.Language;
import java.time.LocalDate;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;

/** Tests how an act cites the acts that amend it, and from when they apply. */
class ActTest {
  private static final ActIdentifier SECURITY = new ActIdentifier("32008R0300");
  private static final ActIdentifier SUPPLEMENT = new ActIdentifier("32009R0272");
  private static final ActIdentifier DIRECTIVE = new ActIdentifier("32010L0018");
  private static final ActIdentifier ONE_DAY = new ActIdentifier("32022L2556");
  private static final ActIdentifier PAYMENTS = new ActIdentifier("32015L2366");
  private static final ActIdentifier EURATOM = new ActIdentifier("31993R0001");
  private static final ActIdentifier UNTITLED = new ActIdentifier("32010R0185");
  private static final ActIdentifier RECENT = new ActIdentifier("32015R1998");

  private static final Act ACT =
      new Act(
          SECURITY,
          null,
          new ResourceType("REG"),
          Map.of(),
          null,
          true,
          null,
          new Timeline(
              List.of(
                  Version.original(
                      LocalDate.of(2008, 4, 29),
                      SECURITY,
                      EnumSet.allOf(Language.class),
                      Map.of()))),
          List.of(),
          Map.of(
              PAYMENTS,
              List.of(date(LocalDate.of(2016, 1, 12), ActDate.Kind.ENTRY_INTO_FORCE, false)),
              ONE_DAY,
              List.of(
                  date(LocalDate.of(2023, 1, 16), ActDate.Kind.ENTRY_INTO_FORCE, false),
                  date(LocalDate.of(2025, 1, 17), ActDate.Kind.TRANSPOSITION, false)),
              DIRECTIVE,
              List.of(
                  date(LocalDate.of(2010, 4, 7), ActDate.Kind.ENTRY_INTO_FORCE, false),
                  date(LocalDate.of(2012, 3, 8), ActDate.Kind.TRANSPOSITION, false),
                  date(LocalDate.of(2013, 3, 8), ActDate.Kind.TRANSPOSITION, false)),
              SUPPLEMENT,
              List.of(
                  date(LocalDate.of(2009, 4, 22), ActDate.Kind.ENTRY_INTO_FORCE, false),
                  date(LocalDate.of(2009, 6, 1), ActDate.Kind.APPLICATION, true)),
              RECENT,
              List.of(
                  date(LocalDate.of(2015, 11, 15), ActDate.Kind.ENTRY_INTO_FORCE, false),
                  date(LocalDate.of(2016, 2, 1), ActDate.Kind.APPLICATION, false)),
              EURATOM,
              List.of(
                  date(LocalDate.of(1993, 1, 7), ActDate.Kind.ENTRY_INTO_FORCE, false),
                  date(null, ActDate.Kind.APPLICATION, false))),
          List.of(),
          Map.of(
              SUPPLEMENT,
              published(
                  "Commission Regulation (EC) No\u00A0272/2009 of 2 April 2009 supplementing the"
                      + " common basic standards on civil aviation security"),
              DIRECTIVE,
              published(
                  "Council Directive 2010/18/EU of 8 March 2010 implementing the revised Framework"
                      + " Agreement on parental leave"),
              EURATOM,
              published("Council Regulation (Euratom) No 1/1993 of 4 January 1993 on a test"),
              RECENT,
              published("Commission Implementing Regulation (EU) 2015/1998 of 5 November 2015")));

  private static ActDate date(@Nullable LocalDate day, ActDate.Kind kind, boolean partial) {
    return new ActDate(
        day, kind, partial, new ActDate.Clause(null, null, List.of(), List.of(), ""));
  }

  private static Publication published(String title) {
    return new Publication(null, Map.of(Language.ENG, title), null);
  }

  @Test
  void citesAnOlderActWithItsKindFromItsTitle() {
    assertThat(ACT.cited(SUPPLEMENT)).isEqualTo("Reg. (EC) 272/2009");
    assertThat(ACT.cited(DIRECTIVE)).isEqualTo("Dir. 2010/18/EU");
    assertThat(ACT.cited(EURATOM)).isEqualTo("Reg. (Euratom) 1/1993");
  }

  @Test
  void citesAnActFrom2015AndAnActWithoutATitleByItsNumberAlone() {
    assertThat(ACT.cited(RECENT)).isEqualTo("2015/1998");
    assertThat(ACT.cited(UNTITLED)).isEqualTo("185/2010");
  }

  @Test
  void anActAppliesFromItsDateOfApplicationForTheWholeAct() {
    assertThat(ACT.appliesFrom(RECENT).map(ActDate::date)).hasValue(LocalDate.of(2016, 2, 1));
  }

  @Test
  void aDirectiveAppliesFromTheDayTheMemberStatesApplyIt() {
    assertThat(ACT.appliesFrom(ONE_DAY).map(ActDate::date)).hasValue(LocalDate.of(2025, 1, 17));
  }

  @Test
  void aDirectiveThatTheMemberStatesApplyFromMoreThanOneDayHasNoDateForTheWholeAct() {
    assertThat(ACT.appliesFrom(DIRECTIVE)).isEmpty();
  }

  @Test
  void aDirectiveWithOnlyAnEntryIntoForceHasNoDateForTheWholeAct() {
    assertThat(ACT.appliesFrom(PAYMENTS)).isEmpty();
  }

  @Test
  void anActWithADateForAPartOnlyAppliesFromItsEntryIntoForce() {
    assertThat(ACT.appliesFrom(SUPPLEMENT).map(ActDate::date)).hasValue(LocalDate.of(2009, 4, 22));
  }

  @Test
  void anActWithAnUnknownDateOfApplicationHasNoDateFromWhichItApplies() {
    assertThat(ACT.appliesFrom(EURATOM)).isEmpty();
    assertThat(ACT.appliesFrom(UNTITLED)).isEmpty();
  }
}
