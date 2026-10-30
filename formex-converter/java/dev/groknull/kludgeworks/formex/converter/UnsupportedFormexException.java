// SPDX-License-Identifier: MIT
package dev.groknull.kludgeworks.formex.converter;

/** The document uses Formex the converter doesn't handle yet; the message names the element. */
public final class UnsupportedFormexException extends ConversionException {

  public UnsupportedFormexException(String message, Throwable cause) {
    super(message, cause);
  }
}
