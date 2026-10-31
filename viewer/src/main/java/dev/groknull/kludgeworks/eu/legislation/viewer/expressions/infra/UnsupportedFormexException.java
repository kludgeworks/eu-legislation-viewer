// SPDX-License-Identifier: MIT
package dev.groknull.kludgeworks.eu.legislation.viewer.expressions.infra;

/** The document uses Formex the converter doesn't handle yet; the message names the element. */
public final class UnsupportedFormexException extends ConversionException {

  public UnsupportedFormexException(String message, Throwable cause) {
    super(message, cause);
  }

  /** As kept by ValkeyExpressions, read back: the cause stayed with the conversion. */
  public UnsupportedFormexException(String message) {
    super(message);
  }
}
