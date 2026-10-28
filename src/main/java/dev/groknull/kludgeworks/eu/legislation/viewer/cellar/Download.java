// SPDX-License-Identifier: MIT
package dev.groknull.kludgeworks.eu.legislation.viewer.cellar;

import java.time.Instant;
import org.jspecify.annotations.Nullable;

/** What a conditional GET of one file gave: the file, or word that the copy we have is current. */
public sealed interface Download {

  /**
   * The file, written to the target as the bytes Cellar sent.
   *
   * @param etag its ETag, for the next conditional GET
   * @param lastModified Cellar's Last-Modified
   * @param contentType as Cellar labels it; a zip entry is labelled application/zip whatever it is
   * @param size in bytes
   * @param sha1 of the bytes, hexadecimal: to compare with the RDF's tdm:stream_fixity
   */
  record Downloaded(
      @Nullable String etag,
      @Nullable Instant lastModified,
      @Nullable String contentType,
      long size,
      String sha1)
      implements Download {}

  /** Cellar answered 304: the ETag still matches, the target was left alone. */
  record NotModified() implements Download {}
}
