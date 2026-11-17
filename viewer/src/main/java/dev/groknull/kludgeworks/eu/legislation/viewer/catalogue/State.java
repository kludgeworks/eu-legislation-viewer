// SPDX-License-Identifier: MIT
package dev.groknull.kludgeworks.eu.legislation.viewer.catalogue;

/**
 * The state of an act, or of one change that an amending act makes, on a day. {@link Act#stateOn}
 * gives it.
 */
public enum State {
  /** The act is not in force yet on the day. */
  NOT_IN_FORCE,
  /** The act is in force, and it does not apply yet on the day. */
  NOT_APPLYING,
  /** It applies on the day. */
  APPLIES,
  /**
   * Cellar does not tell. For example, the act applies in stages, the day is between two stages,
   * and Cellar does not tell which stage the change is in.
   */
  NOT_KNOWN
}
