// SPDX-License-Identifier: MIT
package dev.groknull.kludgeworks.eu.legislation.viewer.image.model;

/** Pixel and display size limits for Formex TIFF conversion. */
public final class ImageLimits {
  /** Maximum decoded raster pixel count ({@code width * height}) allowed for a source TIFF. */
  public static final long MAX_PIXELS = 16_000_000L;

  /** Maximum edge length in pixels for preview images. */
  public static final int PREVIEW_MAX_EDGE = 1600;

  /**
   * Target screen DPI for preview images. Formex TIFFs are published at 400 DPI for print; scaling
   * to 120 DPI matches EUR-Lex consolidated HTML sizing against 16px body text.
   */
  public static final double PREVIEW_DPI = 120.0;

  private ImageLimits() {}
}
