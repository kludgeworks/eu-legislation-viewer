// SPDX-License-Identifier: MIT
package dev.groknull.kludgeworks.eu.legislation.viewer.catalogue;

import static org.assertj.core.api.Assertions.assertThat;

import dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.TheStateOnADay.Stands;
import dev.groknull.kludgeworks.eu.legislation.viewer.language.Language;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;
import java.time.LocalDate;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import org.jspecify.annotations.Nullable;

/**
 * The steps of the rules of the state on a day: the state of an act, or of one of its changes
 * ({@link TheStateOnADay#stateOnADay}), and how an amending act stands ({@link
 * TheStateOnADay#howAnAmendingActStands}, {@link TheStateOnADay.Stands#state}). The changes are
 * those to the English text. They read the act of the scenario ({@link TheAct}).
 */
public class StateSteps {
  private final TheAct the;
  private @Nullable Stands standing;

  public StateSteps(TheAct the) {
    this.the = the;
  }

  @Then("on {day}, {act} {state}")
  public void state(LocalDate day, ActIdentifier act, State state) {
    var own = the.act().id().equals(act);
    assertThat(
            (own ? the.dated(act).state(the.act()) : the.dated(act).state(Set.of()))
                .on(day)
                .state())
        .as("%s on %s", act.cited(), day)
        .isEqualTo(state);
  }

  @Then("{act} repealed {act}")
  public void repealed(ActIdentifier repealing, ActIdentifier act) {
    assertThat(the.act().id()).as("the act").isEqualTo(act);
    assertThat(the.act().repealedBy()).containsExactly(repealing);
  }

  @Then("on {day}, the change of {act} at {place} {state}")
  public void stateOfChange(LocalDate day, ActIdentifier act, Place place, State state) {
    var days = ActDates.days(change(act, place));
    assertThat(the.dated(act).state(days).on(day).state())
        .as("%s at %s on %s", act.cited(), place.words(), day)
        .isEqualTo(state);
  }

  @Then("Cellar gives {day} for the change of {act} at {place}")
  public void dayOfPlace(LocalDate day, ActIdentifier act, Place place) {
    assertThat(ActDates.days(change(act, place))).as(place.words()).containsExactly(day);
  }

  @Then("Cellar gives no day for the change of {act} at {place}")
  public void noDayOfPlace(ActIdentifier act, Place place) {
    assertThat(ActDates.days(change(act, place))).as(place.words()).isEmpty();
  }

  @Then("{act} applies from more than one day: {days}")
  public void parts(ActIdentifier act, List<LocalDate> days) {
    assertThat(the.dated(act).applying(Set.of()))
        .get()
        .isInstanceOfSatisfying(
            Applying.FromMoreThanOneDay.class,
            parts ->
                assertThat(parts.starts().stream().map(Applying.Start::day))
                    .containsExactlyElementsOf(days));
  }

  @Then("a clause of {act} sets one more day that Cellar does not give")
  public void openDay(ActIdentifier act) {
    assertThat(the.act().dates().datesOf(act))
        .as(act.cited())
        .anySatisfy(date -> {
          assertThat(date.kind()).isNotEqualTo(ActDate.Kind.ENTRY_INTO_FORCE);
          assertThat(date.date()).isNull();
        });
  }

  @Then("Cellar lists no changed place for {act}")
  public void noChangedPlace(ActIdentifier act) {
    assertThat(the.changes(act, Language.ENG)).as(act.cited()).isEmpty();
  }

  @When("the reader asks how {act} stands on {day}")
  public void stands(ActIdentifier act, LocalDate day) {
    standing = ActOverTime.amending(the.act(), act, Language.ENG).stands().on(day);
  }

  @Then(
      "{int} of its changes apply, {int} do not apply yet and {int} have a state that is not known")
  public void counts(int applies, int notApplying, int notKnown) {
    var stands = standing();
    assertThat(List.of(stands.applies(), stands.notApplying(), stands.notKnown()))
        .containsExactly(applies, notApplying, notKnown);
  }

  @Then("as a whole it {state}")
  public void whole(State state) {
    assertThat(standing().state()).isEqualTo(state);
  }

  private Stands standing() {
    return Objects.requireNonNull(standing, "No standing: ask how the act stands first");
  }

  /** Returns the one change of {@code act} to the English text at {@code place}. */
  private Amendment change(ActIdentifier act, Place place) {
    var found =
        the.changes(act, Language.ENG).stream()
            .filter(each -> each.place().words().equals(place.words()))
            .toList();
    assertThat(found).as("%s at %s", act.cited(), place.words()).hasSize(1);
    return found.getFirst();
  }
}
