// SPDX-License-Identifier: MIT
package dev.groknull.kludgeworks.formex.converter;

/** A Formex document could not be converted. */
public class ConversionException extends RuntimeException {

  public ConversionException(String message, Throwable cause) {
    super(message, cause);
  }
}
