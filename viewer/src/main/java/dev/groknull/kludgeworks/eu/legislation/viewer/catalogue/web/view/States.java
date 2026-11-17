// SPDX-License-Identifier: MIT
package dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.web.view;

import dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.Act;
import dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.ActDate;
import dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.ActIdentifier;
import dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.Applying;
import dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.Standing;
import dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.State;
import dev.groknull.kludgeworks.eu.legislation.viewer.site.Dates;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import org.jspecify.annotations.Nullable;

/**
 * The wording of the state of an act, or of one of its changes, on a day. The history of an act and
 * the page of an amending act use it. The state is the act's, {@link Act#stateOn} and {@link
 * Act#standingOn}. This class only puts it into words.
 *
 * <p>The tense obeys the day. For today or a later day, "does not apply yet". For a day in the
 * past, "did not apply yet".
 */
final class States {
  /** The tone of a state that needs care: the change does not apply on the day. */
  static final String WARN = "warn";

  /** The tone of a state that Cellar does not tell. */
  static final String OPEN = "open";

  /** The tone of an act that is not in force yet. */
  static final String OFF = "off";

  private States() {}

  /**
   * Returns the label of how an act stands. For example, "Applies in part" or "In force, did not
   * apply yet".
   *
   * @param dated whether Cellar has a date of the act
   */
  static String label(Standing.Whole whole, boolean dated, boolean past) {
    return switch (whole) {
      case NOT_IN_FORCE -> past ? "Was not in force yet" : "Not in force yet";
      case APPLIES -> past ? "Applied" : "Applies";
      case IN_PART -> past ? "Applied in part" : "Applies in part";
      case NOT_APPLYING -> past ? "In force, did not apply yet" : "In force, does not apply yet";
      case NOT_KNOWN -> !dated ? "No dates" : past ? "Was in force" : "In force";
    };
  }

  /** Returns how an act stands as the middle of a sentence. For example, "applies in part". */
  static String phrase(Standing.Whole whole, boolean past) {
    return switch (whole) {
      case NOT_IN_FORCE -> past ? "was not in force yet" : "is not in force yet";
      case APPLIES -> past ? "applied" : "applies";
      case IN_PART -> past ? "applied in part" : "applies in part";
      case NOT_APPLYING ->
          past ? "was in force and did not apply yet" : "is in force and does not apply yet";
      case NOT_KNOWN -> past ? "was in force" : "is in force";
    };
  }

  /** Returns the tone of how an act stands, or null for an act that applies. */
  static @Nullable String tone(Standing.Whole whole) {
    return switch (whole) {
      case IN_PART, NOT_APPLYING -> WARN;
      case NOT_KNOWN -> OPEN;
      case NOT_IN_FORCE -> OFF;
      case APPLIES -> null;
    };
  }

  /** Returns the tone of the state of one change, or null for a change that applies. */
  static @Nullable String tone(State state) {
    return switch (state) {
      case NOT_APPLYING -> WARN;
      case NOT_KNOWN -> OPEN;
      case NOT_IN_FORCE -> OFF;
      case APPLIES -> null;
    };
  }

  /**
   * Returns how many changes are in each state, where they are not all in one state. For example,
   * "Applies: 1 · Does not apply yet: 2". Returns an empty text where they are in one state.
   */
  static String counts(Standing standing, boolean past) {
    var parts = new ArrayList<String>();
    if (standing.applies() > 0) {
      parts.add((past ? "Applied: " : "Applies: ") + standing.applies());
    }
    if (standing.notApplying() > 0) {
      parts.add((past ? "Did not apply yet: " : "Does not apply yet: ") + standing.notApplying());
    }
    if (standing.notKnown() > 0) {
      parts.add("State not given: " + standing.notKnown());
    }
    return parts.size() < 2 ? "" : String.join(" · ", parts);
  }

  /**
   * Returns the dates of {@code act} with the clause that sets them, as its history shows them.
   * {@code applying} is when it applies, or null if Cellar has no dates of it. For example:
   *
   * <ul>
   *   <li>"From 1 June 2017 · Article 2 of 2017/815"
   *   <li>"In stages: 1 August 2022, 15 October 2026 · Article 2 of 2022/1190"
   *   <li>"Article 6 of 2021/1133 sets the day it applies. The data does not give that day."
   * </ul>
   */
  static String dates(ActIdentifier act, @Nullable Applying applying) {
    if (applying == null) {
      return "The data has no dates for this act.";
    }
    var clause = Wording.clause(applying.dates(), act);
    var cited = clause.isEmpty() ? "" : " · " + clause;
    return switch (applying) {
      case Applying.From from -> "From " + Dates.inWords(from.day()) + cited;
      case Applying.InStages stages ->
          "In stages: "
              + stages.stages().stream()
                  .map(stage -> Dates.inWords(stage.day()))
                  .collect(Collectors.joining(", "))
              + cited;
      case Applying.SetBy _ ->
          clause.isEmpty()
              ? "The data does not give the day it applies."
              : clause + " sets the day it applies. The data does not give that day.";
      case Applying.InForce inForce ->
          "In force from "
              + Dates.inWords(inForce.day())
              + cited
              + ". The data does not give the day it applies.";
    };
  }

  /**
   * Returns why the page does not say if a change of {@code act} applies on the day. {@code
   * applying} is when the act applies, or null if Cellar has no dates of it. For example:
   *
   * <ul>
   *   <li>"Applies from a day that Article 3 of 2019/413 sets. The data does not give that day."
   *   <li>"The data does not give the stage. In stages: 1 August 2022, 15 October 2026 · Article 2
   *       of 2022/1190"
   *   <li>"The data does not give the day from which this applies."
   * </ul>
   */
  static String unknown(ActIdentifier act, @Nullable Applying applying) {
    if (applying == null) {
      return "The data has no dates for this act.";
    }
    var clause = Wording.clause(applying.dates(), act);
    return switch (applying) {
      case Applying.InStages _ -> "The data does not give the stage. " + dates(act, applying);
      case Applying.SetBy _ when !clause.isEmpty() ->
          "Applies from a day that " + clause + " sets. The data does not give that day.";
      case Applying.SetBy _, Applying.From _, Applying.InForce _ ->
          "The data does not give the day from which this applies.";
    };
  }

  /**
   * Returns the state on {@code day} of one change of {@code amending}, with the date and the
   * clause, as sentences. {@code placeDays} are the days that Cellar gives for the place of the
   * change. For example, "Does not apply yet. Applies from 15 October 2026 · Article 2 of
   * 2022/1190." Returns null for a change that applies, where the data gives no day for it.
   */
  static @Nullable String note(
      Act act, ActIdentifier amending, Set<LocalDate> placeDays, LocalDate day, boolean past) {
    var from = act.stageOf(amending, placeDays);
    return switch (act.stateOn(amending, placeDays, day)) {
      case APPLIES ->
          from.map(
                  each ->
                      (past ? "Applied from " : "Applies from ")
                          + Dates.inWords(each)
                          + clauseOn(act, amending, each)
                          + ".")
              .orElse(null);
      case NOT_APPLYING ->
          (past ? "Did not apply yet." : "Does not apply yet.")
              + from.map(
                      each ->
                          " Applies from "
                              + Dates.inWords(each)
                              + clauseOn(act, amending, each)
                              + ".")
                  .orElse("");
      case NOT_IN_FORCE -> past ? "Was not in force yet." : "Not in force yet.";
      case NOT_KNOWN -> unknown(amending, act.applying(amending, Set.of()).orElse(null));
    };
  }

  /**
   * Returns the clause of {@code act} that sets its dates on {@code day}, with a separator before
   * it. For example, " · Article 99 of 2016/679". Returns an empty text if Cellar names none.
   */
  static String clauseOn(Act base, ActIdentifier act, LocalDate day) {
    List<ActDate> on = base.datesOf(act).stream().filter(date -> day.equals(date.date())).toList();
    var clause = Wording.clause(on, act);
    return clause.isEmpty() ? "" : " · " + clause;
  }
}
