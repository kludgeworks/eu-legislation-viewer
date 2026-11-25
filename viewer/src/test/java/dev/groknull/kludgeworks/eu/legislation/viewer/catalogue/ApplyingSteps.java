// SPDX-License-Identifier: MIT
package dev.groknull.kludgeworks.eu.legislation.viewer.catalogue;

import static org.assertj.core.api.Assertions.assertThat;

import io.cucumber.java.en.Then;
import java.time.LocalDate;
import java.util.List;
import java.util.Objects;

/**
 * The steps of the rules of when an act applies: the day from which a whole act applies ({@link
 * WhenAnActApplies#wholeActAppliesFrom}), the days from which a provision can apply ({@link
 * WhenAnActApplies#aDayTheActCanApplyFrom}), and the dates that a later act amended ({@link
 * ActDate.Clause#amendedBy}) or set ({@link ActDate.Clause#setBy}). They read the dates of the act
 * of the scenario ({@link TheAct}).
 */
public class ApplyingSteps {
  private final TheAct the;

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
}
