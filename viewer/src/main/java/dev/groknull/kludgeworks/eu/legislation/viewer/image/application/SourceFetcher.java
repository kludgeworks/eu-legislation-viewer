// SPDX-License-Identifier: MIT
package dev.groknull.kludgeworks.eu.legislation.viewer.image.application;

import java.net.URI;

/** Fetches raw image bytes from upstream Cellar items. */
public interface SourceFetcher {

  /** Downloads the image bytes at {@code uri}, failing if the stream exceeds {@code maxBytes}. */
  byte[] fetch(URI uri, long maxBytes);
}
