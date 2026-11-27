// SPDX-License-Identifier: MIT
package dev.groknull.kludgeworks.eu.legislation.viewer.image;

/** Thrown when Cellar does not answer or image conversion cannot run at this time. */
public class ImageUnavailableException extends RuntimeException {
  public ImageUnavailableException(String message) {
    super(message);
  }

  public ImageUnavailableException(String message, Throwable cause) {
    super(message, cause);
  }
}
