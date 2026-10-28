// SPDX-License-Identifier: MIT
package dev.groknull.kludgeworks.eu.legislation.viewer.cellar;

/** Cellar answered in a way the viewer can't use, or not at all. */
public class CellarException extends RuntimeException {
  public CellarException(String message) {
    super(message);
  }

  public CellarException(String message, Throwable cause) {
    super(message, cause);
  }
}
