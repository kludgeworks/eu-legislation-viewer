// SPDX-License-Identifier: MIT
package dev.groknull.kludgeworks.eu.legislation.viewer.expressions.infra;

/** A Formex document could not be converted. */
public class ConversionException extends RuntimeException {

  public ConversionException(String message, Throwable cause) {
    super(message, cause);
  }

  /** As kept by ValkeyExpressions, read back: the cause stayed with the conversion. */
  public ConversionException(String message) {
    super(message);
  }
}
