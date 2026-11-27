// SPDX-License-Identifier: MIT
package dev.groknull.kludgeworks.eu.legislation.viewer.image.application;

import dev.groknull.kludgeworks.eu.legislation.viewer.content.TextId;
import dev.groknull.kludgeworks.eu.legislation.viewer.image.Images;
import dev.groknull.kludgeworks.eu.legislation.viewer.image.model.ImageSource;
import java.net.URI;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/** Persistence port for image source locations and converted WebP images in Postgres. */
public interface ImageStore {

  /** Replaces all image source locations for {@code text}. */
  void replaceSources(TextId text, URI manifestation, List<Images.Source> sources);

  /** Returns the saved source location for {@code file} in {@code text}, if present. */
  Optional<ImageSource> findSource(TextId text, String file);

  /** Returns the saved source SHA-256 bytes of {@code file} in {@code text}, if known. */
  Optional<byte[]> findSourceDigest(TextId text, String file);

  /** Returns the Cellar item addresses of all images saved for {@code text}, keyed by file name. */
  Map<String, URI> sourceUris(TextId text);

  /** Records the source SHA-256 bytes on {@code image_source} on first download. */
  void recordSourceDigest(TextId text, String file, byte[] sha256);

  /** Marks an image source permanently unavailable so Cellar is not asked again. */
  void markGone(TextId text, String file, String reason);

  /** Deletes all image source locations of {@code text}. */
  void removeSources(TextId text);

  /** Returns the stored WebP image if present. */
  Optional<Images.Rendition> findRendition(byte[] sha256, Images.Variant variant, String recipe);

  /** Updates {@code used_at} only when it is older than {@code minAge}. */
  void touchRenditionIfStale(byte[] sha256, Images.Variant variant, String recipe, Duration minAge);

  /**
   * Saves a converted WebP image and deletes the oldest cached entries in batches when total bytes
   * exceed {@code maxBytes}.
   */
  void storeRendition(
      byte[] sha256,
      Images.Variant variant,
      String recipe,
      int width,
      int height,
      byte[] webp,
      long maxBytes,
      int evictionBatch);
}
