// SPDX-License-Identifier: MIT
package dev.groknull.kludgeworks.eu.legislation.viewer.ingest.model;

/** The document uses Formex the converter doesn't handle yet; the message names the element. */
public final class UnsupportedFormexException extends ConversionException {

  public UnsupportedFormexException(String message, Throwable cause) {
    super(message, cause);
  }
}
