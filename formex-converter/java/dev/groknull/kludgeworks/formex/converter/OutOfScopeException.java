// SPDX-License-Identifier: MIT
package dev.groknull.kludgeworks.formex.converter;

/** The document isn't a legal act: a notice, case law, the OJ's table of contents and the like. */
public final class OutOfScopeException extends ConversionException {

  public OutOfScopeException(String message, Throwable cause) {
    super(message, cause);
  }
}
