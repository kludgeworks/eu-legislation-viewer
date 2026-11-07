// SPDX-License-Identifier: MIT
package dev.groknull.kludgeworks.eu.legislation.viewer.ingest.model;

/**
 * The document uses Formex that the converter does not handle yet. The message names the element.
 */
public final class UnsupportedFormexException extends ConversionException {

  public UnsupportedFormexException(String message, Throwable cause) {
    super(message, cause);
  }
}
