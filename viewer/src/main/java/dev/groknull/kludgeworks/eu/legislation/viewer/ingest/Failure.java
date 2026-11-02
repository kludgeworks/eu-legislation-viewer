// SPDX-License-Identifier: MIT
package dev.groknull.kludgeworks.eu.legislation.viewer.ingest;

/** Why an expression couldn't be built. */
public enum Failure {
  /** Cellar has no Formex of it in the language. */
  NO_FORMEX,
  /** It isn't a legal act: a notice, case law, the OJ's table of contents. */
  OUT_OF_SCOPE,
  /** Its Formex uses what the converter doesn't handle yet. */
  UNSUPPORTED,
  /** Anything else, on every attempt. */
  ERROR
}
