// SPDX-License-Identifier: MIT
package dev.groknull.kludgeworks.eu.legislation.viewer.ingest.model;

/**
 * The Formex Cellar files under the CELEX number is another document's: another version of the
 * consolidation, or an OJ act's for a consolidation's number. The message names both.
 */
public final class MisfiledFormexException extends ConversionException {

  public MisfiledFormexException(String message, Throwable cause) {
    super(message, cause);
  }
}
