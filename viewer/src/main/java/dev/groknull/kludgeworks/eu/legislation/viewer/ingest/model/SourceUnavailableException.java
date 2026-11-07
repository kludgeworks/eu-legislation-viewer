// SPDX-License-Identifier: MIT
package dev.groknull.kludgeworks.eu.legislation.viewer.ingest.model;

/**
 * The source of the Formex did not answer. The Publications Office was not reached, too slow, or
 * busy. This may pass, so it is not a refusal of the document ({@link ConversionException}).
 */
public final class SourceUnavailableException extends RuntimeException {

  public SourceUnavailableException(String message, Throwable cause) {
    super(message, cause);
  }
}
