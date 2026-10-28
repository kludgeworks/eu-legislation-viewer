// SPDX-License-Identifier: MIT
package dev.groknull.kludgeworks.eu.legislation.viewer.text.infra;

/** The document uses Formex the normaliser doesn't handle yet; the message names the element. */
public final class UnsupportedFormexException extends NormalisationException {

  public UnsupportedFormexException(String message, Throwable cause) {
    super(message, cause);
  }
}
