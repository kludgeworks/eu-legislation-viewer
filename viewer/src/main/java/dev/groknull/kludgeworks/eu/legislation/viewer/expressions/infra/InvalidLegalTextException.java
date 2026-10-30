// SPDX-License-Identifier: MIT
package dev.groknull.kludgeworks.eu.legislation.viewer.expressions.infra;

/** A document can't be read as legal text: it isn't valid against legal-text.xsd. */
public final class InvalidLegalTextException extends RuntimeException {

  public InvalidLegalTextException(String message, Throwable cause) {
    super(message, cause);
  }
}
