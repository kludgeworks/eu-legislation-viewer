// SPDX-License-Identifier: MIT
package dev.groknull.kludgeworks.eu.legislation.viewer.image;

import dev.groknull.kludgeworks.eu.legislation.viewer.content.TextId;
import java.net.URI;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.jmolecules.ddd.annotation.ValueObject;
import org.jspecify.annotations.Nullable;

/** Keeps Formex image locations for stored texts and serves WebP images when requested. */
public interface Images {

  /**
   * Saves the image locations of {@code text} from its Cellar manifestation. Replaces any prior
   * rows for {@code text}.
   */
  void register(TextId text, URI manifestation, List<Source> sources);

  /**
   * Returns the WebP image for {@code file} in {@code text} at {@code variant}. Reads from Cellar
   * and converts on cache miss. Returns empty if {@code text} has no such image file.
   *
   * @throws ImageGoneException if the upstream image is permanently missing or invalid
   * @throws ImageUnavailableException if Cellar does not answer or conversion cannot run now
   */
  Optional<Rendition> rendition(TextId text, String file, Variant variant);

  /** Returns the recorded SHA-256 hex digest of {@code file} in {@code text}, if known. */
  Optional<String> sourceDigest(TextId text, String file);

  /** Returns the Cellar item addresses of all images saved for {@code text}, keyed by file name. */
  Map<String, URI> sourceUris(TextId text);

  /** Returns the current version string of the TIFF to WebP converter. */
  String version();

  /**
   * One image file in a Cellar Formex manifestation.
   *
   * @param file the Formex {@code FILEREF} name, for example {@code L_2009330EN.01008102.tif}
   * @param uri the Cellar item or zip entry address
   * @param mediaType the MIME media type, for example {@code image/tiff}
   * @param rdfSha1 the SHA-1 fixity digest from Cellar metadata if present, or null
   */
  @ValueObject
  record Source(String file, URI uri, String mediaType, @Nullable String rdfSha1) {}

  /** Which size of a converted image to serve. */
  enum Variant {
    /**
     * Preview image, scaled from its source DPI to 120 DPI with its longest side at most 1600 px.
     */
    PREVIEW,
    /** Full-size image as published in Formex. */
    ORIGINAL
  }

  /**
   * A converted WebP image.
   *
   * @param webp the encoded WebP bytes
   * @param sourceSha256 the hex SHA-256 digest of the source TIFF bytes
   * @param recipe the conversion version that produced {@code webp}
   * @param width the width in pixels
   * @param height the height in pixels
   */
  @ValueObject
  record Rendition(byte[] webp, String sourceSha256, String recipe, int width, int height) {}
}
