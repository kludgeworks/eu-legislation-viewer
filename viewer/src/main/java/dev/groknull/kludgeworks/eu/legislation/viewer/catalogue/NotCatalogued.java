// SPDX-License-Identifier: MIT
package dev.groknull.kludgeworks.eu.legislation.viewer.catalogue;

import java.io.Serial;
import org.jspecify.annotations.Nullable;

/**
 * Says that the catalogue does not have the act yet, and that the viewer looks it up ({@link
 * CataloguedActs}). A page answers with the page that waits for the act. The API answers 202.
 */
public final class NotCatalogued extends RuntimeException {
  @Serial private static final long serialVersionUID = 1L;

  /** The path of the ELI of the act, or null if it was asked for by its CELEX number. */
  private final @Nullable String eli;

  public NotCatalogued(Eli eli) {
    super("Looking up " + eli.path(), null, false, false);
    this.eli = eli.path();
  }

  /** Says that the viewer looks up an act asked for by its CELEX number. */
  public NotCatalogued() {
    super("Looking up an act", null, false, false);
    this.eli = null;
  }

  /** Returns the ELI of the act, or null if it was asked for by its CELEX number. */
  public @Nullable Eli eli() {
    return eli == null ? null : new Eli(eli);
  }
}
