// SPDX-License-Identifier: MIT
package dev.groknull.kludgeworks.eu.legislation.viewer.ingest;

/** The reason why an expression could not be built. */
public enum Failure {
  /** Cellar has no Formex of it in the language. */
  NO_FORMEX,
  /**
   * It is not a legal act. For example, it is a notice, case law or the table of contents of the
   * OJ.
   */
  OUT_OF_SCOPE,
  /**
   * The Formex that Cellar has under its CELEX number belongs to another document. It is another
   * version of the consolidation, or the Formex of an OJ act. For example, 02013D0255-20160529
   * holds the version of 2016-11-17.
   */
  MISFILED,
  /** Its Formex uses something that the converter does not handle yet. */
  UNSUPPORTED,
  /**
   * The Publications Office did not answer on any attempt. It was not reached, too slow, or busy.
   */
  UNAVAILABLE,
  /** Anything else, on every attempt. */
  ERROR;

  /**
   * Returns whether another attempt may help. The Publications Office may answer, or a fault may be
   * fixed.
   */
  public boolean retryable() {
    return switch (this) {
      case UNAVAILABLE, ERROR -> true;
      case NO_FORMEX, OUT_OF_SCOPE, MISFILED, UNSUPPORTED -> false;
    };
  }
}
