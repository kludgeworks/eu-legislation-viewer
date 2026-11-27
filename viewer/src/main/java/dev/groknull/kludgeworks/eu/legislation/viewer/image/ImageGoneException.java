// SPDX-License-Identifier: MIT
package dev.groknull.kludgeworks.eu.legislation.viewer.image;

/**
 * Thrown when an image cannot be served permanently, for example HTTP 404 or 410 from Cellar,
 * invalid or oversized source dimensions, unreadable TIFF data, or a digest mismatch.
 */
public class ImageGoneException extends RuntimeException {
  public ImageGoneException(String message) {
    super(message);
  }

  public ImageGoneException(String message, Throwable cause) {
    super(message, cause);
  }
}
