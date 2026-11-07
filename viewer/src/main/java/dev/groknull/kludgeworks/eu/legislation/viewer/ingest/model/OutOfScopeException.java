// SPDX-License-Identifier: MIT
package dev.groknull.kludgeworks.eu.legislation.viewer.ingest.model;

/**
 * The document is not a legal act. For example, it is a notice, case law or the table of contents
 * of the OJ.
 */
public final class OutOfScopeException extends ConversionException {

  public OutOfScopeException(String message, Throwable cause) {
    super(message, cause);
  }
}
