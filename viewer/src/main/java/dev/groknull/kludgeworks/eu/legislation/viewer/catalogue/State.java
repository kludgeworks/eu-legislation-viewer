// SPDX-License-Identifier: MIT
package dev.groknull.kludgeworks.eu.legislation.viewer.catalogue;

/**
 * The legal state of an act, or of one change that an amending act makes, on a day. {@link
 * Act#stateOn} gives the state of one change, and {@link Standing#state} the state of an amending
 * act from the states of its changes. "state" is the legal state only: how far a text or a
 * preparation is, is its progress.
 */
public enum State {
  /** The act is not in force yet on the day. */
  NOT_IN_FORCE,
  /** The act is in force, and it does not apply yet on the day. */
  NOT_APPLYING,
  /** It applies on the day. */
  APPLIES,
  /**
   * Some of the changes of the act apply on the day, and some do not apply yet. Only an act as a
   * whole is in this state, never one change.
   */
  IN_PART,
  /**
   * Cellar does not tell. For example, the act applies from more than one day, the day is between
   * two of them, and Cellar does not tell which part the change is in.
   */
  NOT_KNOWN
}
