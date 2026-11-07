// SPDX-License-Identifier: MIT
package dev.groknull.kludgeworks.eu.legislation.viewer.ingest.model;

/**
 * The Formex that Cellar files under the CELEX number belongs to another document. It is another
 * version of the consolidation, or the Formex of an OJ act under the number of a consolidation. The
 * message names both documents.
 */
public final class MisfiledFormexException extends ConversionException {

  public MisfiledFormexException(String message, Throwable cause) {
    super(message, cause);
  }
}
