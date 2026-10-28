// SPDX-License-Identifier: MIT
package dev.groknull.kludgeworks.eu.legislation.viewer.text.infra;

/** A Formex document could not be normalised. */
public class NormalisationException extends RuntimeException {

  public NormalisationException(String message, Throwable cause) {
    super(message, cause);
  }
}
