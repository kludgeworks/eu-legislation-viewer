// SPDX-License-Identifier: MIT
package dev.groknull.kludgeworks.eu.legislation.viewer.image.application;

import dev.groknull.kludgeworks.eu.legislation.viewer.image.Images;

/** Decodes source TIFF images and encodes lossless WebP renditions. */
public interface ImageRenderer {

  /**
   * Decodes {@code tiffBytes}, optionally scales down for {@code variant}, and encodes lossless
   * WebP.
   */
  Rendered render(byte[] tiffBytes, Images.Variant variant);

  /**
   * Encoded WebP output with final dimensions.
   *
   * @param webp the encoded WebP bytes
   * @param width final pixel width
   * @param height final pixel height
   */
  record Rendered(byte[] webp, int width, int height) {}
}
