// SPDX-License-Identifier: MIT
package dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.web.view;

import dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.Act;
import dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.ActDates;
import dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.ActHistory;
import dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.ActIdentifier;
import dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.Standing;
import dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.State;
import dev.groknull.kludgeworks.eu.legislation.viewer.site.Dates;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Set;
import org.jspecify.annotations.Nullable;

/**
 * The wording of the state of an act, or of one of its changes, on a day: its label, its phrase,
 * its tone, and the statement of one change. The history of an act and the page of an amending act
 * use it. The state is the act's, {@link ActDates#stateOn} and {@link ActHistory#standingOn}. This
 * class only puts it into words.
 *
 * <p>The tense obeys the day. For today or a later day, "does not apply yet". For a day in the
 * past, "did not apply yet".
 */
final class StateWording {
  /** The tone of a state that needs care: the change does not apply on the day. */
  static final String WARN = "warn";

  /** The tone of a state that Cellar does not tell. */
  static final String UNKNOWN = "unknown";

  /** The tone of an act that is not in force yet. */
  static final String NOT_IN_FORCE = "not-in-force";

  private StateWording() {}

  /**
   * Returns the label of how an act stands. For example, "Applies in part" or "In force, did not
   * apply yet".
   *
   * @param dated whether Cellar has a date of the act
   */
  static String label(State state, boolean dated, boolean past) {
    return switch (state) {
      case NOT_IN_FORCE -> past ? "Was not in force yet" : "Not in force yet";
      case APPLIES -> past ? "Applied" : "Applies";
      case IN_PART -> past ? "Applied in part" : "Applies in part";
      case NOT_APPLYING -> past ? "In force, did not apply yet" : "In force, does not apply yet";
      case NOT_KNOWN -> !dated ? "No dates" : past ? "Was in force" : "In force";
    };
  }

  /** Returns how an act stands as the middle of a sentence. For example, "applies in part". */
  static String phrase(State state, boolean past) {
    return switch (state) {
      case NOT_IN_FORCE -> past ? "was not in force yet" : "is not in force yet";
      case APPLIES -> past ? "applied" : "applies";
      case IN_PART -> past ? "applied in part" : "applies in part";
      case NOT_APPLYING ->
          past ? "was in force and did not apply yet" : "is in force and does not apply yet";
      case NOT_KNOWN -> past ? "was in force" : "is in force";
    };
  }

  /**
   * Returns the tone of a state: the class that gives the state its colour, or null for a state
   * that applies.
   */
  static @Nullable String tone(State state) {
    return switch (state) {
      case IN_PART, NOT_APPLYING -> WARN;
      case NOT_KNOWN -> UNKNOWN;
      case NOT_IN_FORCE -> NOT_IN_FORCE;
      case APPLIES -> null;
    };
  }

  /**
   * Returns how many changes are in each state, where they are not all in one state. For example,
   * "Applies: 1 · Does not apply yet: 2". Returns an empty text where they are in one state.
   */
  static String counts(Standing standing, boolean past) {
    var lines = new ArrayList<String>();
    if (standing.applies() > 0) {
      lines.add((past ? "Applied: " : "Applies: ") + standing.applies());
    }
    if (standing.notApplying() > 0) {
      lines.add((past ? "Did not apply yet: " : "Does not apply yet: ") + standing.notApplying());
    }
    if (standing.notKnown() > 0) {
      lines.add("State not known: " + standing.notKnown());
    }
    return lines.size() < 2 ? "" : String.join(" · ", lines);
  }

  /**
   * Returns the state on {@code day} of one change of {@code amending}, with the date and the
   * clause, as sentences. {@code placeDays} are the days that Cellar gives for the place of the
   * change. For example, "Does not apply yet. Applies from 15 October 2026 · Article 2 of
   * 2022/1190." Returns null for a change that applies, where the data gives no day for it, and
   * where {@link Wording#unknown} has no words.
   */
  static @Nullable String statement(
      Act act, ActIdentifier amending, Set<LocalDate> placeDays, LocalDate day, boolean past) {
    var from = act.dates().changeAppliesFrom(amending, placeDays);
    return switch (act.dates().stateOn(amending, placeDays, day)) {
      case APPLIES ->
          from.map(each ->
                  (past ? "Applied from " : "Applies from ")
                      + Dates.inWords(each)
                      + Wording.clauseOn(act, amending, each)
                      + ".")
              .orElse(null);
      case NOT_APPLYING ->
          (past ? "Did not apply yet." : "Does not apply yet.")
              + from.map(each ->
                      " Applies from "
                          + Dates.inWords(each)
                          + Wording.clauseOn(act, amending, each)
                          + ".")
                  .orElse("");
      case NOT_IN_FORCE -> past ? "Was not in force yet." : "Not in force yet.";
      case NOT_KNOWN ->
          Wording.unknown(amending, act.dates().applying(amending, Set.of()).orElse(null));
      case IN_PART -> throw new IllegalStateException("The state of one change is never in part");
    };
  }
}
