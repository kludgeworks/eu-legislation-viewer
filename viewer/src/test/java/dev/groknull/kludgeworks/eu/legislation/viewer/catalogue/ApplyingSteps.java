// SPDX-License-Identifier: MIT
package dev.groknull.kludgeworks.eu.legislation.viewer.catalogue;

import static org.assertj.core.api.Assertions.assertThat;

import io.cucumber.java.en.Given;
import io.cucumber.java.en.Then;
import java.time.LocalDate;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import org.jspecify.annotations.Nullable;

/**
 * The steps of the rules of when an act applies: the day from which a whole act applies ({@link
 * WhenAnActApplies#wholeActAppliesFrom}), the days from which a provision can apply ({@link
 * WhenAnActApplies#aDayTheActCanApplyFrom}), and the dates that a later act amended ({@link
 * ActDate.Clause#amendedBy}) or set ({@link ActDate.Clause#setBy}). They read the dates of the act
 * of the scenario ({@link TheAct}).
 */
public class ApplyingSteps {
  private final TheAct the;
  private @Nullable ActIdentifier changed;
  private Set<LocalDate> placeDays = Set.of();
  private @Nullable ActDate rawDate;
  private @Nullable LocalDate publishedDay;
  private @Nullable ActDate resolvedDate;

  public ApplyingSteps(TheAct the) {
    this.the = the;
  }

  @Then("{act} applies from {day}")
  public void appliesFrom(ActIdentifier act, LocalDate day) {
    assertThat(the.dated(act).appliesFrom().from().map(ActDate::date))
        .as(act.cited())
        .hasValue(day);
  }

  @Then("{act} has no day from which the whole act applies")
  public void noDay(ActIdentifier act) {
    assertThat(the.dated(act).appliesFrom().from().map(ActDate::date)).as(act.cited()).isEmpty();
  }

  @Then("{act} has a date for a part on {day}")
  public void forPart(ActIdentifier act, LocalDate day) {
    assertThat(the.act().dates().datesOf(act))
        .as(act.cited())
        .anySatisfy(date -> {
          assertThat(date.date()).isEqualTo(day);
          assertThat(date.forPart()).isTrue();
        });
  }

  @Then("the Member States apply {act} from {days}")
  public void memberStates(ActIdentifier act, List<LocalDate> days) {
    assertThat(
            the.act().dates().datesOf(act).stream()
                .filter(date -> date.kind() == ActDate.Kind.TRANSPOSITION)
                .map(ActDate::date)
                .filter(Objects::nonNull)
                .distinct())
        .as(act.cited())
        .containsExactlyInAnyOrderElementsOf(days);
  }

  @Then("the only date of {act} is its entry into force, on {day}")
  public void onlyEntryIntoForce(ActIdentifier act, LocalDate day) {
    assertThat(the.act().dates().datesOf(act))
        .as(act.cited())
        .singleElement()
        .satisfies(date -> {
          assertThat(date.kind()).isEqualTo(ActDate.Kind.ENTRY_INTO_FORCE);
          assertThat(date.date()).isEqualTo(day);
        });
  }

  @Then("the date of application of {act} is a placeholder")
  public void placeholder(ActIdentifier act) {
    assertThat(the.act().dates().datesOf(act))
        .as(act.cited())
        .anySatisfy(date -> {
          assertThat(date.kind()).isNotEqualTo(ActDate.Kind.ENTRY_INTO_FORCE);
          assertThat(date.date()).isNull();
        });
  }

  @Then("a provision of {act} can apply from {day}")
  public void canApply(ActIdentifier act, LocalDate day) {
    assertThat(the.dated(act).canApplyFrom().contains(day))
        .as("%s on %s", act.cited(), day)
        .isTrue();
  }

  @Then("a provision of {act} cannot apply from {day}")
  public void cannotApply(ActIdentifier act, LocalDate day) {
    assertThat(the.dated(act).canApplyFrom().contains(day))
        .as("%s on %s", act.cited(), day)
        .isFalse();
  }

  @Then("{act} enters into force on {day}")
  public void entersIntoForce(ActIdentifier act, LocalDate day) {
    assertThat(the.act().dates().entryIntoForce(act)).as(act.cited()).hasValue(day);
  }

  @Then("the date of {act} of {day} names {act} as the act that amended it")
  public void amendedBy(ActIdentifier act, LocalDate day, ActIdentifier later) {
    assertThat(datesOn(act, day))
        .allSatisfy(date -> {
          assertThat(date.clause().amendedBy()).containsExactly(later);
          assertThat(date.clause().setBy()).isEmpty();
        });
  }

  @Then("the date of {act} of {day} names {act} as the act that set its day")
  public void setBy(ActIdentifier act, LocalDate day, ActIdentifier later) {
    assertThat(datesOn(act, day))
        .allSatisfy(date -> {
          assertThat(date.clause().setBy()).containsExactly(later);
          assertThat(date.clause().amendedBy()).isEmpty();
        });
  }

  /** Returns the dates of {@code act} on {@code day}. There is at least one. */
  private List<ActDate> datesOn(ActIdentifier act, LocalDate day) {
    var dates =
        the.act().dates().datesOf(act).stream().filter(date -> day.equals(date.date())).toList();
    assertThat(dates).as("the dates of %s on %s", act.cited(), day).isNotEmpty();
    return dates;
  }

  @Given("Cellar dates a change of {act} at its place from {day}")
  public void cellarDay(ActIdentifier act, LocalDate day) {
    changed = act;
    placeDays = Set.of(day);
  }

  @Then("a change of {act} applies from a day that {word} {int} of {act} sets")
  public void changeAppliesFromADaySetBy(
      ActIdentifier act, String type, int number, ActIdentifier setting) {
    assertThat(act).isEqualTo(setting);
    assertThat(applying(act, Set.of()))
        .isInstanceOfSatisfying(
            Applying.SetBy.class,
            setBy ->
                assertThat(setBy.dates())
                    .flatExtracting(date -> date.clause().provisions())
                    .extracting(ActDate.Provision::cited)
                    .containsOnly(type + " " + number));
  }

  @Then("a change of {act} applies from {day}, its entry into force")
  public void changeAppliesFromItsEntryIntoForce(ActIdentifier act, LocalDate day) {
    assertThat(applying(act, Set.of()))
        .isInstanceOfSatisfying(
            Applying.From.class,
            from -> {
              assertThat(from.day()).isEqualTo(day);
              assertThat(from.entryIntoForce()).as("only its entry into force").isTrue();
            });
  }

  @Then("a change of {act} is in force from {day}, and the data does not say from when it applies")
  public void changeIsInForce(ActIdentifier act, LocalDate day) {
    assertThat(applying(act, Set.of()))
        .isInstanceOfSatisfying(
            Applying.InForce.class, inForce -> assertThat(inForce.day()).isEqualTo(day));
  }

  @Then("a change of {act} applies from {day}")
  public void changeAppliesFrom(ActIdentifier act, LocalDate day) {
    appliesFromADay(applying(act, Set.of()), day);
  }

  @Then("that change applies from {day}")
  public void thatChangeAppliesFrom(LocalDate day) {
    appliesFromADay(applying(Objects.requireNonNull(changed, "no change given"), placeDays), day);
  }

  @Then("that change applies from more than one day")
  public void thatChangeAppliesFromMoreThanOneDay(List<Applying.Start> starts) {
    assertThat(applying(Objects.requireNonNull(changed, "no change given"), placeDays))
        .isInstanceOfSatisfying(
            Applying.FromMoreThanOneDay.class,
            parts -> assertThat(parts.starts()).containsExactlyElementsOf(starts));
  }

  private static void appliesFromADay(Applying applying, LocalDate day) {
    assertThat(applying)
        .isInstanceOfSatisfying(
            Applying.From.class,
            from -> {
              assertThat(from.day()).isEqualTo(day);
              assertThat(from.entryIntoForce()).as("only its entry into force").isFalse();
            });
  }

  /**
   * Returns when a change of {@code act} applies, as its dates tell. {@code days} are the days that
   * Cellar gives for the change at its place.
   */
  private Applying applying(ActIdentifier act, Set<LocalDate> days) {
    var applying = the.dated(act).applying(days);
    assertThat(applying).as("when a change of %s applies", act.cited()).isPresent();
    return applying.orElseThrow();
  }

  @Given("Cellar gives {day} for {int} day(s) after publication on {day}")
  public void cellarPublicationDate(LocalDate day, int days, LocalDate published) {
    this.rawDate =
        new ActDate(
            day,
            ActDate.Kind.ENTRY_INTO_FORCE,
            false,
            new ActDate.Clause(
                ActDate.Anchor.PUBLICATION,
                days,
                List.of(),
                List.of(),
                List.of(),
                "DATPUB +" + days));
    this.publishedDay = published;
  }

  @Given("the enacted text states {int} day(s) after publication")
  public void enactedDaysAfterPublication(int days) {
    this.resolvedDate =
        WhenAnActApplies.resolveAgainstEnactedText(
            Objects.requireNonNull(rawDate),
            publishedDay,
            new WhenAnActApplies.EnactedDate.OnPublication(days));
  }

  @Given("the enacted text states the fixed date {day}")
  public void enactedFixedDate(LocalDate day) {
    this.resolvedDate =
        WhenAnActApplies.resolveAgainstEnactedText(
            Objects.requireNonNull(rawDate),
            publishedDay,
            new WhenAnActApplies.EnactedDate.Fixed(day));
  }

  @Given("the enacted text gives no known rule")
  public void enactedUnknown() {
    this.resolvedDate =
        WhenAnActApplies.resolveAgainstEnactedText(
            Objects.requireNonNull(rawDate),
            publishedDay,
            new WhenAnActApplies.EnactedDate.Unknown());
  }

  @Then("the resolved date is {day} from {word}")
  public void resolvedDateIs(LocalDate expectedDay, String expectedSource) {
    var actual = Objects.requireNonNull(resolvedDate);
    assertThat(actual.date()).isEqualTo(expectedDay);
    assertThat(actual.source())
        .isEqualTo(ActDate.Source.valueOf(expectedSource.toUpperCase(java.util.Locale.ROOT)));
  }
}
