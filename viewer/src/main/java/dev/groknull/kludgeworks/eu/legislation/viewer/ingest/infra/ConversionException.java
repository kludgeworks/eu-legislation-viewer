// SPDX-License-Identifier: MIT
package dev.groknull.kludgeworks.eu.legislation.viewer.ingest.infra;

/** A Formex document could not be converted. */
public class ConversionException extends RuntimeException {

  public ConversionException(String message, Throwable cause) {
    super(message, cause);
  }

  public ConversionException(String message) {
    super(message);
  }
}
