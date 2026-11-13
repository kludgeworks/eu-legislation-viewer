// SPDX-License-Identifier: MIT
package dev.groknull.kludgeworks.eu.legislation.viewer.content.application;

/**
 * The caches that {@link ContentService} uses, by name. The infrastructure configures them. They
 * are keyed by text identities, which are never used again, so none is invalidated.
 */
public final class ContentCaches {
  /** The cache of outlines, by text identity. */
  public static final String OUTLINES = "outlines";

  private ContentCaches() {}
}
