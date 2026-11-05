// SPDX-License-Identifier: MIT
package dev.groknull.kludgeworks.eu.legislation.viewer.ingest.model;

/**
 * The Formex's source didn't answer: the Publications Office not reached, too slow, or busy. It may
 * pass, so it is no refusal of the document's (ConversionException).
 */
public final class SourceUnavailableException extends RuntimeException {

  public SourceUnavailableException(String message, Throwable cause) {
    super(message, cause);
  }
}
