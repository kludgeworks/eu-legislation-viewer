// SPDX-License-Identifier: MIT
package dev.groknull.kludgeworks.eu.legislation.viewer.catalogue;

import org.jmolecules.ddd.annotation.ValueObject;

/**
 * How an amending act stands on a day: how many of its changes are in each {@link State}. {@link
 * Act#standingOn} gives it. An act for which Cellar lists no changed place counts as one change.
 *
 * @param notInForce whether the act is not in force yet on the day. Then no change applies.
 * @param applies how many of its changes apply on the day
 * @param notApplying how many do not apply yet
 * @param notKnown how many have a state that Cellar does not tell
 */
@ValueObject
public record Standing(boolean notInForce, int applies, int notApplying, int notKnown) {

  /** The state of the act as a whole. */
  public enum Whole {
    /** The act is not in force yet. */
    NOT_IN_FORCE,
    /** Each of its changes applies. */
    APPLIES,
    /** Some of its changes apply, and some do not apply yet. */
    IN_PART,
    /** None of its changes applies yet. */
    NOT_APPLYING,
    /** Cellar does not tell the state of each change, so the state of the act is not known. */
    NOT_KNOWN
  }

  /**
   * Returns the state of the act as a whole. It is known only if the state of each change is known,
   * with one exception: an act with a change that applies and a change that does not apply yet
   * applies in part, whatever the other changes do.
   */
  public Whole whole() {
    if (notInForce) {
      return Whole.NOT_IN_FORCE;
    }
    if (applies > 0 && notApplying > 0) {
      return Whole.IN_PART;
    }
    if (notKnown > 0) {
      return Whole.NOT_KNOWN;
    }
    return applies > 0 ? Whole.APPLIES : Whole.NOT_APPLYING;
  }
}
