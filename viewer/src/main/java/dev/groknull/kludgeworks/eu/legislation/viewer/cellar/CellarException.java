// SPDX-License-Identifier: MIT
package dev.groknull.kludgeworks.eu.legislation.viewer.cellar;

/** An error that tells that Cellar gave an answer the viewer cannot use, or gave no answer. */
public class CellarException extends RuntimeException {
  public CellarException(String message) {
    super(message);
  }

  public CellarException(String message, Throwable cause) {
    super(message, cause);
  }
}
